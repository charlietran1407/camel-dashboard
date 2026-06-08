package vn.cxn.apache_camel.service.route_command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.enums.ClusterEventType;
import vn.cxn.apache_camel.service.CamelRouteService;

@Component
public class DeleteVersionCommandImpl implements ClusterRouteCommand {

    private static final Logger log = LoggerFactory.getLogger(DeleteVersionCommandImpl.class);

    private final CamelRouteService camelRouteService;

    @Value("${camel.dashboard.storage-dir:./camel-routes-storage}")
    private String storageDir;

    public DeleteVersionCommandImpl(CamelRouteService camelRouteService) {
        this.camelRouteService = camelRouteService;
    }

    @Override
    public ClusterEventType eventType() {
        return ClusterEventType.DELETE_VERSION;
    }

    @Override
    public void execute(RouteCommandContext context) {
        removeRoutes(context);
        deleteLocalCacheFile(context);
    }

    private void removeRoutes(RouteCommandContext context) {
        String routeIdsStr = (String) context.eventMap().get("meta_routeIds");
        if (routeIdsStr == null || routeIdsStr.isBlank()) {
            return;
        }
        for (String routeId : routeIdsStr.split(",")) {
            try {
                camelRouteService.removeRoute(routeId);
            } catch (Exception e) {
                log.warn(
                        "DeleteVersionCommandImpl: Error removing route '{}' during DELETE_VERSION:"
                                + " {}",
                        routeId,
                        e.getMessage());
            }
        }
    }

    private void deleteLocalCacheFile(RouteCommandContext context) {
        String fileName = (String) context.eventMap().get("meta_fileName");
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Path cacheFile = Paths.get(storageDir, fileName);
            Files.deleteIfExists(cacheFile);
            log.info("DeleteVersionCommandImpl: Cleaned up local file cache '{}'", fileName);
        } catch (Exception e) {
            log.warn(
                    "DeleteVersionCommandImpl: Failed to delete local file cache '{}': {}",
                    fileName,
                    e.getMessage());
        }
    }
}
