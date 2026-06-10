package vn.cxn.apache_camel.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.cxn.apache_camel.model.dto.RouteInfo;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.CamelRouteMermaidParser;
import vn.cxn.apache_camel.service.RouteLifecycleService;
import vn.cxn.apache_camel.service.RouteQueryService;
import vn.cxn.apache_camel.service.RouteVersionService;

@RestController
@RequestMapping("/api/routes")
public class RouteManagementController {

    private final RouteLifecycleService routeLifecycleService;
    private final RouteQueryService routeQueryService;
    private final RouteVersionService versionService;

    public RouteManagementController(
            RouteLifecycleService routeLifecycleService,
            RouteQueryService routeQueryService,
            RouteVersionService versionService) {
        this.routeLifecycleService = routeLifecycleService;
        this.routeQueryService = routeQueryService;
        this.versionService = versionService;
    }

    /** GET /api/routes - list all routes with status */
    @GetMapping
    public ResponseEntity<List<RouteInfo>> listRoutes() {
        return ResponseEntity.ok(routeQueryService.listRoutes());
    }

    /** GET /api/routes/{routeId}/status */
    @GetMapping("/{routeId}/status")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable String routeId) {
        String status = routeQueryService.getRouteStatus(routeId);
        return ResponseEntity.ok(Map.of("routeId", routeId, "status", status));
    }

    /** POST /api/routes/{routeId}/start */
    @PostMapping("/{routeId}/start")
    public ResponseEntity<Map<String, String>> startRoute(@PathVariable String routeId) {
        try {
            routeLifecycleService.startRoute(routeId);
            return ResponseEntity.ok(
                    Map.of(
                            "routeId",
                            routeId,
                            "status",
                            "Started",
                            "message",
                            "Route started successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /api/routes/{routeId}/stop */
    @PostMapping("/{routeId}/stop")
    public ResponseEntity<Map<String, String>> stopRoute(@PathVariable String routeId) {
        try {
            routeLifecycleService.stopRoute(routeId);
            return ResponseEntity.ok(
                    Map.of(
                            "routeId",
                            routeId,
                            "status",
                            "Stopped",
                            "message",
                            "Route stopped successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /api/routes/{routeId}/suspend */
    @PostMapping("/{routeId}/suspend")
    public ResponseEntity<Map<String, String>> suspendRoute(@PathVariable String routeId) {
        try {
            routeLifecycleService.suspendRoute(routeId);
            return ResponseEntity.ok(
                    Map.of(
                            "routeId",
                            routeId,
                            "status",
                            "Suspended",
                            "message",
                            "Route suspended"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /api/routes/{routeId}/resume */
    @PostMapping("/{routeId}/resume")
    public ResponseEntity<Map<String, String>> resumeRoute(@PathVariable String routeId) {
        try {
            routeLifecycleService.resumeRoute(routeId);
            return ResponseEntity.ok(
                    Map.of("routeId", routeId, "status", "Started", "message", "Route resumed"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** DELETE /api/routes/{routeId} */
    /*
     * @DeleteMapping("/{routeId}")
     * public ResponseEntity<Map<String, Object>> deleteRoute(@PathVariable String
     * routeId) {
     * try {
     * boolean removed = routeLifecycleService.removeRoute(routeId);
     * if (removed) {
     * return ResponseEntity.ok(
     * Map.of("routeId", routeId, "removed", true, "message", "Route removed"));
     * } else {
     * return ResponseEntity.status(404)
     * .body(Map.of("error", "Route not found: " + routeId));
     * }
     * } catch (Exception e) {
     * return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
     * }
     * }
     */

    /** POST /api/routes/deploy/{versionId} - deploy from a specific uploaded version */
    @PostMapping("/deploy/{versionId}")
    public ResponseEntity<?> deployFromVersion(@PathVariable String versionId) {
        try {
            String routeId = routeLifecycleService.deployFromVersion(versionId);
            return ResponseEntity.ok(
                    Map.of(
                            "routeId", routeId,
                            "versionId", versionId,
                            "message", "Route deployed successfully"));
        } catch (vn.cxn.apache_camel.validation.MissingComponentsDownloadedException e) {
            // Components were missing but have now been downloaded to the loader.path dir.
            // The route is saved; the app must be restarted to activate the new JARs.
            return ResponseEntity.accepted()
                    .body(
                            Map.of(
                                    "status",
                                    "RESTART_REQUIRED",
                                    "message",
                                    e.getMessage(),
                                    "downloadedArtifacts",
                                    e.getDownloadedArtifacts(),
                                    "action",
                                    "Restart the application, then re-deploy version "
                                            + versionId));
        } catch (vn.cxn.apache_camel.validation.PreDeployValidationException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "error", e.getMessage(),
                                    "validationResult", e.getValidationResult()));
        } catch (IllegalArgumentException e) {
            // Covers: version not found AND YAML parse/load errors
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Covers: disk I/O errors reading the stored YAML
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Storage error: " + e.getMessage()));
        } catch (Exception e) {
            String msg =
                    e.getCause() != null
                            ? e.getMessage() + " | Caused by: " + e.getCause().getMessage()
                            : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("error", "Deployment failed: " + msg));
        }
    }

    /** GET /api/routes/{routeId}/source */
    @GetMapping("/{routeId}/source")
    public ResponseEntity<?> getRouteSource(@PathVariable String routeId) {
        try {
            Optional<RouteVersion> activeOpt = versionService.getActiveVersionByRouteId(routeId);

            if (activeOpt.isPresent()) {
                RouteVersion rv = activeOpt.get();
                String content = versionService.getContentFromDisk(rv.getId());
                return ResponseEntity.ok(
                        Map.of(
                                "routeId",
                                routeId,
                                "serviceId",
                                rv.getServiceId() != null ? rv.getServiceId() : "",
                                "fileName",
                                rv.getFileName(),
                                "version",
                                rv.getVersion(),
                                "content",
                                content));
            } else {
                return ResponseEntity.status(404)
                        .body(
                                Map.of(
                                        "error",
                                        "No active persistent version found for route: "
                                                + routeId));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve source: " + e.getMessage()));
        }
    }

    /** GET /api/routes/{routeId}/mermaid */
    @GetMapping("/{routeId}/mermaid")
    public ResponseEntity<?> getRouteMermaid(@PathVariable String routeId) {
        try {
            Optional<RouteVersion> activeOpt = versionService.getActiveVersionByRouteId(routeId);

            if (activeOpt.isPresent()) {
                RouteVersion rv = activeOpt.get();
                String content = versionService.getContentFromDisk(rv.getId());
                String mermaidCode = CamelRouteMermaidParser.parse(rv.getFileName(), content);
                return ResponseEntity.ok(
                        Map.of(
                                "routeId", routeId,
                                "mermaidCode", mermaidCode));
            } else {
                String fallbackMermaid =
                        "flowchart TD\n"
                                + "  fallbackNode[\"No active persistent version found for route: "
                                + routeId
                                + "\"]:::step\n"
                                + "  classDef step"
                                + " fill:#1a1d27,stroke:#374151,stroke-width:1px,color:#e2e8f0;\n";
                return ResponseEntity.ok(
                        Map.of(
                                "routeId", routeId,
                                "mermaidCode", fallbackMermaid));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to generate flowchart: " + e.getMessage()));
        }
    }

    /**
     * GET /api/routes/rest-services - Retrieve dynamic REST and regular routes grouped by service
     */
    @GetMapping("/rest-services")
    public ResponseEntity<List<Map<String, Object>>> getRestServices() {
        return ResponseEntity.ok(routeQueryService.getServicesWithDetails());
    }
}
