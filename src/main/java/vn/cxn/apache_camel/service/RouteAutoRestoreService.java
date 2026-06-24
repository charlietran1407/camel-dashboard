package vn.cxn.apache_camel.service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.dto.RouteVersion;

@Service
@ConditionalOnProperty(name = "route.restore.enabled", havingValue = "true", matchIfMissing = true)
public class RouteAutoRestoreService {

    private static final Logger log = LoggerFactory.getLogger(RouteAutoRestoreService.class);

    private final CamelRouteService camelRouteService;
    private final RouteVersionService versionService;
    private final org.apache.camel.CamelContext camelContext;
    private final io.opentelemetry.api.trace.Tracer tracer;
    private final long restoreDelayMs;
    private final Executor taskExecutor;

    public RouteAutoRestoreService(
            CamelRouteService camelRouteService,
            RouteVersionService versionService,
            org.apache.camel.CamelContext camelContext,
            io.opentelemetry.api.trace.Tracer tracer,
            @Value("${route.restore.delay-ms:2000}") long restoreDelayMs,
            @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        this.camelRouteService = camelRouteService;
        this.versionService = versionService;
        this.camelContext = camelContext;
        this.tracer = tracer;
        this.restoreDelayMs = restoreDelayMs;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Auto-restore all routes marked as auto-restore after the app fully starts. Uses
     * ApplicationReadyEvent so Camel context is guaranteed to be running.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreActiveRoutes() {
        CompletableFuture.runAsync(
                () -> {
                    if (restoreDelayMs > 0) {
                        try {
                            // Delay to ensure all system components are stable
                            Thread.sleep(restoreDelayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn(
                                    "[Route-Restore] DelayedExecution - Interrupted - Restore"
                                            + " active routes was interrupted");
                            return;
                        }
                    }

                    List<RouteVersion> allVersions = versionService.getAllVersions();

                    // Collect the latest auto-restore version per serviceId (falling back to
                    // routeId)
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
                        log.info(
                                "[Route-Restore] Restore - Skip - No persisted auto-restore"
                                        + " services/routes to restore.");
                        return;
                    }

                    log.info(
                            "[Route-Restore] Restore - Start - Restoring {} persisted active"
                                    + " version(s)...",
                            activeByGroup.size());
                    List<RouteVersion> restoredVersions = new ArrayList<>();

                    io.opentelemetry.api.trace.Span span =
                            tracer.spanBuilder("restore-active-routes").startSpan();
                    try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                        activeByGroup.forEach(
                                (groupKey, version) -> {
                                    try {
                                        // Use serviceId or a fallback for logging
                                        String identifier =
                                                version.getServiceId() != null
                                                        ? version.getServiceId()
                                                        : version.getFileName();

                                        // Skip if any route from this version is already in
                                        // context (basic check)
                                        if (version.getRouteIds() != null
                                                && !version.getRouteIds().isEmpty()) {
                                            String firstId = version.getRouteIds().get(0);
                                            if (camelContext.getRoute(firstId) != null) {
                                                log.info(
                                                        "[Route-Restore] Restore - Skip -"
                                                            + " Service/Route group '{}' already in"
                                                            + " context, skipping restore.",
                                                        identifier);
                                                return;
                                            }
                                        }

                                        camelRouteService.restoreRouteVersion(
                                                version, restoredVersions);
                                        restoredVersions.add(version);
                                        log.info(
                                                "[Route-Restore] Restore - Success - Restored"
                                                        + " group '{}' (v{})",
                                                identifier,
                                                version.getVersion());
                                    } catch (Exception e) {
                                        log.error(
                                                "[Route-Restore] Restore - Failed - Failed to"
                                                        + " restore version '{}': {}",
                                                version.getId(),
                                                e.getMessage());
                                        span.recordException(e);
                                        span.setStatus(
                                                io.opentelemetry.api.trace.StatusCode.ERROR,
                                                "Failed to restore version: "
                                                        + version.getId()
                                                        + " - "
                                                        + e.getMessage());
                                    }
                                });
                        log.info("[Route-Restore] Restore - Success - Route restore complete.");
                    } catch (Throwable t) {
                        log.error(
                                "[Route-Restore] Restore - Failed - Fatal error during route"
                                        + " auto-restoration process",
                                t);
                        span.recordException(t);
                        span.setStatus(
                                io.opentelemetry.api.trace.StatusCode.ERROR,
                                "Fatal restoration process failure: " + t.getMessage());
                    } finally {
                        span.end();
                    }
                },
                taskExecutor);
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
