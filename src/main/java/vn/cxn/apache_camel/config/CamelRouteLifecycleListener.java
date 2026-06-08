package vn.cxn.apache_camel.config;

import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.service.ClusterNodeService;
import vn.cxn.apache_camel.service.DynamicConnectionManager;
import vn.cxn.apache_camel.service.SystemLogService;

@Component
public class CamelRouteLifecycleListener extends EventNotifierSupport {

    private static final Logger log = LoggerFactory.getLogger(CamelRouteLifecycleListener.class);

    private final ClusterNodeService clusterNodeService;
    private final SystemLogService systemLogService;
    private final DynamicConnectionManager connectionManager;

    public CamelRouteLifecycleListener(
            ClusterNodeService clusterNodeService,
            SystemLogService systemLogService,
            DynamicConnectionManager connectionManager) {
        this.clusterNodeService = clusterNodeService;
        this.systemLogService = systemLogService;
        this.connectionManager = connectionManager;
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (event instanceof CamelEvent.RouteEvent routeEvent) {
            var route = routeEvent.getRoute();
            if (route != null && route.getId() != null) {
                String routeId = route.getId();

                // 1. Sync states reactively
                syncRouteStates();

                // 2. Handle specific route events
                if (event instanceof CamelEvent.RouteStartedEvent) {
                    auditLog(routeId, "Started", "Route started successfully");
                } else if (event instanceof CamelEvent.RouteStoppedEvent) {
                    auditLog(routeId, "Stopped", "Route stopped");
                } else if (event instanceof CamelEvent.RouteRemovedEvent) {
                    auditLog(routeId, "Removed", "Route removed from Camel context");

                    // Automatically release associated connection pools
                    connectionManager.cleanupConnectionsForRoute(routeId);
                }
            }
        }
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof CamelEvent.RouteStartedEvent
                || event instanceof CamelEvent.RouteStoppedEvent
                || event instanceof CamelEvent.RouteRemovedEvent;
    }

    private void syncRouteStates() {
        try {
            clusterNodeService.updateLocalRouteStates();
            log.info("Reactively synchronized local route states after lifecycle change.");
        } catch (Exception e) {
            log.error("Failed to synchronize local route states: {}", e.getMessage());
        }
    }

    private void auditLog(String routeId, String action, String message) {
        try {
            systemLogService.log(
                    "AUDIT", "SUCCESS", routeId, "[" + action + "] " + message, null, null, null);
        } catch (Exception e) {
            log.warn(
                    "Audit logging failed for route lifecycle event '{}': {}",
                    routeId,
                    e.getMessage());
        }
    }
}
