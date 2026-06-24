package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
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
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.BeanInfo;
import vn.cxn.apache_camel.model.entity.BeanEntity;
import vn.cxn.apache_camel.repository.BeanRepository;

@Service
@Transactional(readOnly = true)
public class DynamicBeanService {

    private static final Logger log = LoggerFactory.getLogger(DynamicBeanService.class);

    private final CamelContext camelContext;
    private final BeanRepository beanRepository;

    @Value("${app.initial-mode:false}")
    private boolean initialMode;

    public DynamicBeanService(CamelContext camelContext, BeanRepository beanRepository) {
        this.camelContext = camelContext;
        this.beanRepository = beanRepository;
    }

    // beanId -> the actual loaded instance (kept alive to avoid GC)
    private final Map<String, Object> liveInstances = new ConcurrentHashMap<>();
    // beanId -> ClassLoader (kept alive so classes remain loadable)
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();
    // beanId -> Path of the active temporary JAR file (tracked to prevent disk leaks)
    private final Map<String, Path> tempJarPaths = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (initialMode) {
            log.info("Running in initial mode. Skipping dynamic beans registration.");
            return;
        }
        try {
            // Re-register all previously registered beans on startup
            beanRepository.findAll().stream()
                    .filter(BeanEntity::isRegistered)
                    .forEach(
                            b -> {
                                try {
                                    reloadBean(b);
                                } catch (Exception e) {
                                    log.error(
                                            "Failed to restore bean '{}' on startup: {}",
                                            b.getBeanName(),
                                            e.getMessage());
                                }
                            });
            log.info("DynamicBeanService initialized. Active beans loaded from DB.");
        } catch (Exception e) {
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
    @Transactional
    public BeanInfo uploadBean(
            String jarFileName,
            byte[] jarBytes,
            String beanName,
            String className,
            String description)
            throws Exception {
        // Find existing bean with the same name
        Optional<BeanEntity> existingOpt = beanRepository.findByBeanNameIgnoreCase(beanName);
        if (existingOpt.isPresent()) {
            BeanEntity existing = existingOpt.get();
            log.info(
                    "Found existing bean with name '{}'. Automatically deactivating and deleting"
                            + " it.",
                    beanName);
            try {
                deleteBean(existing.getId().toString());
                beanRepository.flush();
            } catch (Exception e) {
                log.warn(
                        "Failed to delete existing duplicate bean '{}': {}",
                        beanName,
                        e.getMessage());
            }
        }

        UUID beanId = UUID.randomUUID();

        BeanEntity entity = new BeanEntity();
        entity.setId(beanId);
        entity.setBeanName(beanName);
        entity.setClassName(className);
        entity.setJarFileName(jarFileName);
        entity.setJarData(jarBytes);
        entity.setDescription(description);
        entity.setRegistered(false);
        entity.setUploadedAt(Instant.now());

        beanRepository.save(entity);
        log.info(
                "Bean JAR uploaded and saved to DB: beanName={}, class={}, file={}",
                beanName,
                className,
                jarFileName);

        // Automatically register/activate the newly uploaded bean
        try {
            registerBean(beanId.toString());
        } catch (Exception e) {
            log.error(
                    "Failed to automatically register new bean '{}': {}", beanName, e.getMessage());
        }

        return toDto(entity);
    }

    /** Load the bean from its stored JAR and register it in the Camel Registry. */
    @Transactional
    public BeanInfo registerBean(String beanId) throws Exception {
        Optional<BeanEntity> opt = beanRepository.findById(UUID.fromString(beanId));
        if (opt.isEmpty()) throw new IllegalArgumentException("Bean not found: " + beanId);

        BeanEntity entity = opt.get();
        reloadBean(entity);
        entity.setRegistered(true);
        entity.setRegisteredAt(Instant.now());
        beanRepository.save(entity);
        log.info("Bean '{}' registered in Camel Registry", entity.getBeanName());
        return toDto(entity);
    }

    /** Remove a bean from the Camel Registry (but keep its JAR). */
    @Transactional
    public BeanInfo unregisterBean(String beanId) throws Exception {
        Optional<BeanEntity> opt = beanRepository.findById(UUID.fromString(beanId));
        if (opt.isEmpty()) throw new IllegalArgumentException("Bean not found: " + beanId);

        BeanEntity entity = opt.get();

        // Unbind from Camel registry
        try {
            camelContext.getRegistry().unbind(entity.getBeanName());
        } catch (Exception e) {
            log.warn("Could not unbind bean '{}': {}", entity.getBeanName(), e.getMessage());
        }

        cleanupBeanResources(beanId);

        entity.setRegistered(false);
        beanRepository.save(entity);
        log.info("Bean '{}' unregistered from Camel Registry", entity.getBeanName());
        return toDto(entity);
    }

    /** Delete a bean entry and its JAR file. */
    @Transactional
    public boolean deleteBean(String beanId) throws Exception {
        Optional<BeanEntity> opt = beanRepository.findById(UUID.fromString(beanId));
        if (opt.isEmpty()) return false;

        BeanEntity entity = opt.get();
        if (entity.isRegistered()) unregisterBean(beanId);

        cleanupBeanResources(beanId);

        beanRepository.delete(entity);
        return true;
    }

    public List<BeanInfo> getAllBeans() {
        return beanRepository.findAll().stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(BeanInfo::getUploadedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<BeanInfo> getBeanById(String beanId) {
        try {
            return beanRepository.findById(UUID.fromString(beanId)).map(this::toDto);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Scan the classes available in a JAR and return fully-qualified class names. Useful to help
     * the user pick the right className.
     */
    public List<String> listClassesInJar(String beanId) throws IOException {
        Optional<BeanEntity> opt = beanRepository.findById(UUID.fromString(beanId));
        if (opt.isEmpty()) throw new IllegalArgumentException("Bean not found: " + beanId);

        BeanEntity entity = opt.get();
        List<String> classes = new ArrayList<>();

        // Write bytes to temp file to scan classes in JAR
        Path tempJarPath = Files.createTempFile("scan-" + beanId + "-", ".jar");
        try {
            Files.write(tempJarPath, entity.getJarData());
            try (JarFile jar = new JarFile(tempJarPath.toFile())) {
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
        } finally {
            Files.deleteIfExists(tempJarPath);
        }
        return classes;
    }

    // ─── Internal helpers ────────────────────────────────────────

    private void reloadBean(BeanEntity entity) throws Exception {
        String beanId = entity.getId().toString();

        // Close old class loader and delete old temp JAR if any
        cleanupBeanResources(beanId);

        // Write BLOB bytes to a temporary JAR file on the local filesystem
        Path tempJarPath = Files.createTempFile("bean-" + entity.getId() + "-", ".jar");
        Files.write(tempJarPath, entity.getJarData());
        tempJarPaths.put(beanId, tempJarPath);

        // Create isolated class loader for the JAR
        URLClassLoader cl =
                new URLClassLoader(
                        new URL[] {tempJarPath.toUri().toURL()},
                        Thread.currentThread().getContextClassLoader());
        classLoaders.put(beanId, cl);

        // Instantiate the class using the no-arg constructor
        Class<?> clazz = cl.loadClass(entity.getClassName());
        Object instance = clazz.getDeclaredConstructor().newInstance();
        liveInstances.put(beanId, instance);

        // Bind in Camel Registry
        camelContext.getRegistry().bind(entity.getBeanName(), instance);
        log.info(
                "Bound bean '{}' ({}) into Camel Registry",
                entity.getBeanName(),
                entity.getClassName());
    }

    private void cleanupBeanResources(String beanId) {
        URLClassLoader cl = classLoaders.remove(beanId);
        if (cl != null) {
            try {
                cl.close();
            } catch (Exception ignored) {
            }
        }

        Path path = tempJarPaths.remove(beanId);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
                log.info("Deleted temporary JAR file for bean '{}': {}", beanId, path);
            } catch (Exception e) {
                log.warn(
                        "Failed to delete temporary JAR file for bean '{}': {}",
                        beanId,
                        e.getMessage());
            }
        }

        liveInstances.remove(beanId);
    }

    private BeanInfo toDto(BeanEntity entity) {
        if (entity == null) return null;
        BeanInfo info = new BeanInfo();
        info.setId(entity.getId().toString());
        info.setBeanName(entity.getBeanName());
        info.setClassName(entity.getClassName());
        info.setJarFileName(entity.getJarFileName());
        info.setDescription(entity.getDescription());
        info.setRegistered(entity.isRegistered());
        info.setUploadedAt(entity.getUploadedAt());
        info.setRegisteredAt(entity.getRegisteredAt());
        return info;
    }
}
