package vn.cxn.apache_camel.service.component;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.service.component.CamelYamlComponentScanner.ScanResult;

/**
 * Orchestrates the full "detect → resolve → download" pipeline for Camel components that are
 * missing from the current JVM classpath.
 *
 * <h2>How activation works (ZIP / PropertiesLauncher)</h2>
 *
 * <p>JARs are downloaded into the directory configured by {@code camel.dashboard.loader-path}
 * (default: {@code ./camel-ext-libs}). When the application is packaged with {@code
 * <layout>ZIP</layout>} and started with {@code -Dloader.path=<same-dir>}, Spring Boot's {@link
 * org.springframework.boot.loader.launch.PropertiesLauncher} adds every JAR in that directory to
 * the classpath <em>before</em> Spring Boot initialises. A restart is therefore required to
 * activate newly downloaded components.
 */
@Service
public class LoaderPathDependencyService {

    private static final Logger log = LoggerFactory.getLogger(LoaderPathDependencyService.class);

    @Value("${camel.dashboard.loader-path:./camel-ext-libs}")
    private String loaderPath;

    private final CamelYamlComponentScanner scanner;
    private final CamelCatalogCoordinateMapper mapper;

    public LoaderPathDependencyService(
            CamelYamlComponentScanner scanner, CamelCatalogCoordinateMapper mapper) {
        this.scanner = scanner;
        this.mapper = mapper;
    }

    /**
     * Scan {@code yamlContent} for Camel component schemes/EIP hints, resolve their Maven
     * coordinates, download any missing JARs to the loader-path directory, and report what
     * happened.
     *
     * @param yamlContent raw YAML DSL content
     * @return result describing what was downloaded and whether a restart is needed
     */
    public MissingComponentDownloadResult ensureComponentsAvailable(String yamlContent) {
        ScanResult scan = scanner.scan(yamlContent);

        if (scan.isEmpty()) {
            log.debug("No components detected in YAML — nothing to download");
            return new MissingComponentDownloadResult(List.of(), List.of(), false);
        }

        log.info(
                "Detected schemes={}, eipHints={}, dataFormats={}, languages={}"
                        + " — resolving missing coordinates",
                scan.schemes(),
                scan.eipHints(),
                scan.dataFormats(),
                scan.languages());

        List<String> missingCoords = mapper.resolveMissingCoordinates(scan);

        if (missingCoords.isEmpty()) {
            log.info("All required components are already on the classpath");
            List<String> present = new ArrayList<>(scan.schemes());
            present.addAll(scan.eipHints());
            return new MissingComponentDownloadResult(List.of(), present, false);
        }

        log.info("Downloading missing coordinates: {}", missingCoords);
        List<String> downloaded = downloadToLoaderPath(missingCoords);

        return new MissingComponentDownloadResult(downloaded, List.of(), !downloaded.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Download logic
    // -------------------------------------------------------------------------

    private List<String> downloadToLoaderPath(List<String> coordinates) {
        File libDir = new File(loaderPath);
        if (!libDir.exists() && !libDir.mkdirs()) {
            throw new IllegalStateException(
                    "Cannot create loader-path directory: " + libDir.getAbsolutePath());
        }

        List<String> downloaded = new ArrayList<>();

        for (String coord : coordinates) {
            try {
                log.info("Resolving {} with transitive dependencies via Maven...", coord);
                File[] resolvedFiles;
                try {
                    log.info("Resolving {} offline...", coord);
                    resolvedFiles =
                            Maven.configureResolver()
                                    .workOffline()
                                    .resolve(coord)
                                    .withTransitivity()
                                    .asFile();
                } catch (Exception offlineEx) {
                    log.info(
                            "Offline resolution failed for {}, attempting online resolution: {}",
                            coord,
                            offlineEx.getMessage());
                    resolvedFiles = Maven.resolver().resolve(coord).withTransitivity().asFile();
                }

                for (File jar : resolvedFiles) {
                    if (isJarAlreadyOnClasspath(jar)) {
                        log.info("Skipping provided dependency: {}", jar.getName());
                        continue;
                    }
                    Path dest = Paths.get(libDir.getAbsolutePath(), jar.getName());
                    if (!dest.toFile().exists()) {
                        Files.copy(jar.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Downloaded → {}", dest.getFileName());
                    } else {
                        log.debug("Already cached in loader-path: {}", dest.getFileName());
                    }
                }
                downloaded.add(coord);
                log.info("Successfully staged {} into loader-path dir", coord);
            } catch (Exception e) {
                log.error("Failed to download dependency {}: {}", coord, e.getMessage(), e);
                throw new RuntimeException(
                        "Failed to download required Camel component '"
                                + coord
                                + "'. Reason: "
                                + e.getMessage(),
                        e);
            }
        }
        return downloaded;
    }

    private boolean isJarAlreadyOnClasspath(File jar) {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
                    if (Thread.currentThread().getContextClassLoader().getResource(name) != null) {
                        log.debug(
                                "Skipping {} because resource {} is already on the classpath",
                                jar.getName(),
                                name);
                        return true;
                    }
                }
            }

            // Fallback: check only the first 5 classes to avoid scanning the entire JAR
            int classChecked = 0;
            entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class")
                        && !name.contains("$")
                        && !name.startsWith("META-INF/")
                        && !name.equals("module-info.class")) {
                    String className =
                            name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    try {
                        Class.forName(
                                className, false, Thread.currentThread().getContextClassLoader());
                        log.debug(
                                "Skipping {} because {} is already on the classpath",
                                jar.getName(),
                                className);
                        return true;
                    } catch (ClassNotFoundException ignored) {
                        // Class not present; keep searching
                    }
                    classChecked++;
                    if (classChecked >= 5) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to inspect JAR {} for classpath duplicates: {}",
                    jar.getName(),
                    e.getMessage());
        }
        return false;
    }

    /**
     * Returns absolute paths of all JARs currently staged in the loader-path directory. Useful for
     * diagnostics / admin APIs.
     */
    public List<URL> listStagedJars() {
        File libDir = new File(loaderPath);
        if (!libDir.exists()) return List.of();
        File[] jars = libDir.listFiles(f -> f.getName().endsWith(".jar"));
        if (jars == null) return List.of();
        return Arrays.stream(jars)
                .map(
                        f -> {
                            try {
                                return f.toURI().toURL();
                            } catch (Exception e) {
                                return null;
                            }
                        })
                .filter(u -> u != null)
                .toList();
    }

    public String getLoaderPath() {
        return loaderPath;
    }
}
