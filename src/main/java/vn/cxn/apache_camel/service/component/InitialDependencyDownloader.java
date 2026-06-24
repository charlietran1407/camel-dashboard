package vn.cxn.apache_camel.service.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.RouteVersionService;

@Component
public class InitialDependencyDownloader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialDependencyDownloader.class);

    private final RouteVersionService versionService;
    private final LoaderPathDependencyService loaderPathDependencyService;

    @Value("${app.initial-mode:false}")
    private boolean initialMode;

    public InitialDependencyDownloader(
            RouteVersionService versionService,
            LoaderPathDependencyService loaderPathDependencyService) {
        this.versionService = versionService;
        this.loaderPathDependencyService = loaderPathDependencyService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!initialMode) {
            log.info("Not running in initial mode. Skipping dependency pre-download.");
            return;
        }

        log.info("[Initial-Mode] Starting initial dependency resolution phase...");
        try {
            List<RouteVersion> allVersions = versionService.getAllVersions();

            // Filter latest auto-restore versions (same logic as RouteAutoRestoreService)
            Map<String, RouteVersion> activeByGroup = new LinkedHashMap<>();
            allVersions.forEach(
                    v -> {
                        if (v.isAutoRestore()) {
                            String groupKey = getGroupKey(v);
                            activeByGroup.merge(
                                    groupKey,
                                    v,
                                    (existing, incoming) ->
                                            incoming.getVersion() > existing.getVersion()
                                                    ? incoming
                                                    : existing);
                        }
                    });

            if (activeByGroup.isEmpty()) {
                log.info("[Initial-Mode] No active auto-restore routes found to resolve.");
                System.exit(0);
                return;
            }

            log.info(
                    "[Initial-Mode] Found {} active version(s) to process. Scanning for missing"
                            + " dependencies...",
                    activeByGroup.size());

            int totalDownloaded = 0;
            for (RouteVersion version : activeByGroup.values()) {
                String identifier =
                        version.getServiceId() != null
                                ? version.getServiceId()
                                : version.getFileName();
                log.info(
                        "[Initial-Mode] Processing group: {} (version {})",
                        identifier,
                        version.getVersion());

                try {
                    String content = versionService.getDeploymentContent(version);
                    if (content != null && !content.isBlank()) {
                        var downloadResult =
                                loaderPathDependencyService.ensureComponentsAvailable(content);
                        if (!downloadResult.getDownloadedArtifacts().isEmpty()) {
                            log.info(
                                    "[Initial-Mode] Downloaded for {}: {}",
                                    identifier,
                                    downloadResult.getDownloadedArtifacts());
                            totalDownloaded += downloadResult.getDownloadedArtifacts().size();
                        } else {
                            log.info(
                                    "[Initial-Mode] All dependencies for {} are already"
                                            + " available.",
                                    identifier);
                        }
                    }
                } catch (Exception e) {
                    log.error(
                            "[Initial-Mode] Failed to resolve dependencies for group {}: {}",
                            identifier,
                            e.getMessage(),
                            e);
                }
            }

            log.info(
                    "[Initial-Mode] Initial dependency resolution complete. Total artifacts"
                            + " downloaded: {}",
                    totalDownloaded);
            System.exit(0);
        } catch (Exception e) {
            log.error("[Initial-Mode] Fatal error during initial dependency download", e);
            System.exit(1);
        }
    }

    private String getGroupKey(RouteVersion v) {
        if (v.getServiceId() != null) {
            return v.getServiceId();
        }
        if (v.getRouteIds() != null && !v.getRouteIds().isEmpty()) {
            return v.getRouteIds().get(0);
        }
        return v.getFileName();
    }
}
