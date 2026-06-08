package vn.cxn.apache_camel.service;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DependencyDownloadService {

    private static final Logger log = LoggerFactory.getLogger(DependencyDownloadService.class);
    private final String downloadDir = "./camel-routes-storage/dependencies/";

    public List<URL> downloadDependencies(String serviceId, List<String> coordinates) {
        List<URL> jarUrls = new ArrayList<>();
        if (coordinates == null || coordinates.isEmpty()) {
            return jarUrls;
        }

        File serviceLibDir = new File(downloadDir + serviceId);
        if (!serviceLibDir.exists()) {
            serviceLibDir.mkdirs();
        }

        for (String coord : coordinates) {
            if (coord == null || coord.trim().isEmpty()) {
                continue;
            }
            String trimCoord = coord.trim();
            log.info("Resolving Maven coordinate and transitive dependencies for: {}", trimCoord);
            try {
                // ShrinkWrap resolves coordinates and downloads them with transitive dependencies
                File[] resolvedFiles =
                        Maven.resolver().resolve(trimCoord).withTransitivity().asFile();

                for (File file : resolvedFiles) {
                    Path dest = Paths.get(serviceLibDir.getAbsolutePath(), file.getName());
                    if (!dest.toFile().exists()) {
                        Files.copy(file.toPath(), dest);
                        log.info("Downloaded and cached dependency: {}", file.getName());
                    }
                    jarUrls.add(dest.toUri().toURL());
                }
            } catch (Exception e) {
                log.error("Failed to resolve dependency coordinate: {}", trimCoord, e);
                throw new RuntimeException(
                        "Failed to download dependency: "
                                + trimCoord
                                + ". Reason: "
                                + e.getMessage(),
                        e);
            }
        }
        return jarUrls;
    }
}
