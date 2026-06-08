package vn.cxn.apache_camel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.dto.BeanInfo;

@Service
public class DynamicBeanService {

    private static final Logger log = LoggerFactory.getLogger(DynamicBeanService.class);
    private static final String INDEX_FILE = "bean-index.json";

    @Value("${camel.dashboard.storage-dir:./camel-routes-storage}")
    private String storageDir;

    private final CamelContext camelContext;
    private final ObjectMapper objectMapper;

    public DynamicBeanService(CamelContext camelContext, ObjectMapper objectMapper) {
        this.camelContext = camelContext;
        this.objectMapper = objectMapper;
    }

    // beanId -> BeanInfo metadata
    private final Map<String, BeanInfo> beanRegistry = new ConcurrentHashMap<>();
    // beanId -> the actual loaded instance (kept alive to avoid GC)
    private final Map<String, Object> liveInstances = new ConcurrentHashMap<>();
    // beanId -> ClassLoader (kept alive so classes remain loadable)
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(storageDir, "beans"));
            loadIndex();
            // Re-register all previously registered beans on startup
            beanRegistry.values().stream()
                    .filter(BeanInfo::isRegistered)
                    .forEach(
                            b -> {
                                try {
                                    reloadBean(b);
                                } catch (Exception e) {
                                    log.error(
                                            "Failed to restore bean '{}' on startup: {}",
                                            b.getBeanName(),
                                            e.getMessage());
                                    b.setRegistered(false);
                                }
                            });
            saveIndex();
            log.info("DynamicBeanService initialized. {} bean(s) loaded.", beanRegistry.size());
        } catch (IOException e) {
            log.error("Failed to initialize DynamicBeanService", e);
        }
    }

    /**
     * Upload a JAR and store it. Does NOT register the bean yet.
     *
     * @param jarFileName original file name
     * @param jarBytes raw bytes of the .jar
     * @param beanName name to use in Camel registry (used in YAML: bean ref: "beanName")
     * @param className fully-qualified class name to instantiate
     * @param description optional description
     */
    public BeanInfo uploadBean(
            String jarFileName,
            byte[] jarBytes,
            String beanName,
            String className,
            String description)
            throws IOException {
        String beanId = UUID.randomUUID().toString();
        Path jarPath = Paths.get(storageDir, "beans", beanId + ".jar");
        Files.write(jarPath, jarBytes);

        BeanInfo info = new BeanInfo();
        info.setId(beanId);
        info.setBeanName(beanName);
        info.setClassName(className);
        info.setJarFileName(jarFileName);
        info.setDescription(description);
        info.setRegistered(false);
        info.setUploadedAt(Instant.now());

        beanRegistry.put(beanId, info);
        saveIndex();
        log.info(
                "Bean JAR uploaded: beanName={}, class={}, file={}",
                beanName,
                className,
                jarFileName);
        return info;
    }

    /** Load the bean from its stored JAR and register it in the Camel Registry. */
    public BeanInfo registerBean(String beanId) throws Exception {
        BeanInfo info = beanRegistry.get(beanId);
        if (info == null) throw new IllegalArgumentException("Bean not found: " + beanId);

        reloadBean(info);
        info.setRegistered(true);
        info.setRegisteredAt(Instant.now());
        saveIndex();
        log.info("Bean '{}' registered in Camel Registry", info.getBeanName());
        return info;
    }

    /** Remove a bean from the Camel Registry (but keep its JAR). */
    public BeanInfo unregisterBean(String beanId) throws Exception {
        BeanInfo info = beanRegistry.get(beanId);
        if (info == null) throw new IllegalArgumentException("Bean not found: " + beanId);

        // Unbind from Camel registry
        try {
            camelContext.getRegistry().unbind(info.getBeanName());
        } catch (Exception e) {
            log.warn("Could not unbind bean '{}': {}", info.getBeanName(), e.getMessage());
        }

        // Release class loader
        URLClassLoader cl = classLoaders.remove(beanId);
        if (cl != null) {
            try {
                cl.close();
            } catch (Exception ignored) {
            }
        }
        liveInstances.remove(beanId);

        info.setRegistered(false);
        saveIndex();
        log.info("Bean '{}' unregistered from Camel Registry", info.getBeanName());
        return info;
    }

    /** Delete a bean entry and its JAR file. */
    public boolean deleteBean(String beanId) throws Exception {
        BeanInfo info = beanRegistry.get(beanId);
        if (info == null) return false;

        if (info.isRegistered()) unregisterBean(beanId);

        beanRegistry.remove(beanId);
        Files.deleteIfExists(Paths.get(storageDir, "beans", beanId + ".jar"));
        saveIndex();
        return true;
    }

    public List<BeanInfo> getAllBeans() {
        return new ArrayList<>(beanRegistry.values())
                .stream()
                        .sorted(Comparator.comparing(BeanInfo::getUploadedAt).reversed())
                        .collect(Collectors.toList());
    }

    public Optional<BeanInfo> getBeanById(String beanId) {
        return Optional.ofNullable(beanRegistry.get(beanId));
    }

    /**
     * Scan the classes available in a JAR and return fully-qualified class names. Useful to help
     * the user pick the right className.
     */
    public List<String> listClassesInJar(String beanId) throws IOException {
        Path jarPath = Paths.get(storageDir, "beans", beanId + ".jar");
        List<String> classes = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                    String className =
                            entry.getName()
                                    .replace('/', '.')
                                    .replace('\\', '.')
                                    .replaceAll("\\.class$", "");
                    classes.add(className);
                }
            }
        }
        return classes;
    }

    // ─── Internal helpers ────────────────────────────────────────

    private void reloadBean(BeanInfo info) throws Exception {
        Path jarPath = Paths.get(storageDir, "beans", info.getId() + ".jar");
        if (!Files.exists(jarPath)) {
            throw new IllegalStateException(
                    "JAR file missing for bean '" + info.getBeanName() + "'");
        }
        // Close old class loader if any
        URLClassLoader old = classLoaders.remove(info.getId());
        if (old != null) {
            try {
                old.close();
            } catch (Exception ignored) {
            }
        }

        // Create isolated class loader for the JAR
        URLClassLoader cl =
                new URLClassLoader(
                        new URL[] {jarPath.toUri().toURL()},
                        Thread.currentThread().getContextClassLoader());
        classLoaders.put(info.getId(), cl);

        // Instantiate the class using the no-arg constructor
        Class<?> clazz = cl.loadClass(info.getClassName());
        Object instance = clazz.getDeclaredConstructor().newInstance();
        liveInstances.put(info.getId(), instance);

        // Bind in Camel Registry
        camelContext.getRegistry().bind(info.getBeanName(), instance);
        log.info(
                "Bound bean '{}' ({}) into Camel Registry",
                info.getBeanName(),
                info.getClassName());
    }

    private void saveIndex() {
        try {
            File f = new File(storageDir, INDEX_FILE);
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(f, new ArrayList<>(beanRegistry.values()));
        } catch (IOException e) {
            log.error("Failed to save bean index", e);
        }
    }

    private void loadIndex() {
        File f = new File(storageDir, INDEX_FILE);
        if (!f.exists()) return;
        try {
            List<BeanInfo> list = objectMapper.readValue(f, new TypeReference<List<BeanInfo>>() {});
            list.forEach(b -> beanRegistry.put(b.getId(), b));
        } catch (IOException e) {
            log.error("Failed to load bean index", e);
        }
    }
}
