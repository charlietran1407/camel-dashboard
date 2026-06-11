package vn.cxn.apache_camel.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.dto.RouteMetrics;

/**
 * Provides per-route runtime metrics by querying Camel's built-in management layer
 * (ManagedRouteMBean). This gives accurate exchange counters, processing times, and throughput
 * without requiring an external metrics system.
 *
 * <p>Requires camel.springboot.jmx-enabled=true (default) or camel-management on the classpath.
 */
@Service
public class RouteMetricsService {

    private static final Logger log = LoggerFactory.getLogger(RouteMetricsService.class);

    private final CamelContext camelContext;

    public RouteMetricsService(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Returns metrics for a single route, or empty if the route is not found or management is
     * unavailable.
     */
    public Optional<RouteMetrics> getMetricsForRoute(String routeId) {
        ManagedCamelContext mcc =
                camelContext.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc == null) {
            log.debug("ManagedCamelContext not available — JMX/management may be disabled");
            return Optional.empty();
        }

        ManagedRouteMBean managed = mcc.getManagedRoute(routeId, ManagedRouteMBean.class);
        if (managed == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    new RouteMetrics(
                            routeId,
                            managed.getExchangesTotal(),
                            managed.getExchangesCompleted(),
                            managed.getExchangesFailed(),
                            managed.getExchangesInflight(),
                            managed.getMeanProcessingTime(),
                            managed.getMinProcessingTime(),
                            managed.getMaxProcessingTime(),
                            managed.getLastProcessingTime(),
                            managed.getThroughput(),
                            managed.getLoad01(),
                            managed.getLoad05(),
                            managed.getLoad15()));
        } catch (Exception e) {
            log.warn("Failed to read metrics for route '{}': {}", routeId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns metrics for all currently live routes. Routes without metrics are silently skipped.
     */
    public List<RouteMetrics> getAllRouteMetrics() {
        return camelContext.getRoutes().stream()
                .map(route -> getMetricsForRoute(route.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}
