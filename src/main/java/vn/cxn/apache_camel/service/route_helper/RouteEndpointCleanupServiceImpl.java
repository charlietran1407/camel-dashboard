package vn.cxn.apache_camel.service.route_helper;

import java.util.ArrayList;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RouteEndpointCleanupServiceImpl implements RouteEndpointCleanupService {

    private static final Logger log =
            LoggerFactory.getLogger(RouteEndpointCleanupServiceImpl.class);

    private final CamelContext camelContext;

    public RouteEndpointCleanupServiceImpl(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void cleanUpEndpointsForRoute(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return;
        }
        try {
            List<Endpoint> toRemove = new ArrayList<>();
            org.apache.camel.Route route = camelContext.getRoute(routeId);
            if (route != null && route.getEndpoint() != null) {
                toRemove.add(route.getEndpoint());
            }
            for (Endpoint endpoint : camelContext.getEndpoints()) {
                String uri = endpoint.getEndpointUri();
                if (uri != null && uri.contains("routeId=" + routeId)) {
                    if (!toRemove.contains(endpoint)) {
                        toRemove.add(endpoint);
                    }
                }
            }
            for (Endpoint endpoint : toRemove) {
                log.info(
                        "Removing dynamic endpoint '{}' from CamelContext registry",
                        endpoint.getEndpointUri());
                try {
                    camelContext.removeEndpoints(endpoint.getEndpointUri());
                } catch (Exception e) {
                    log.warn(
                            "Failed to remove endpoint '{}' from registry: {}",
                            endpoint.getEndpointUri(),
                            e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error during endpoint cleanup for routeId '{}': {}", routeId, e.getMessage());
        }
    }
}
