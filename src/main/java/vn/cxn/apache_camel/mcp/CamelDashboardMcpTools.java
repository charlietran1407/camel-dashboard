package vn.cxn.apache_camel.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.dto.ServiceDTO;
import vn.cxn.apache_camel.service.RouteLifecycleService;
import vn.cxn.apache_camel.service.RouteQueryService;
import vn.cxn.apache_camel.service.RouteValidationService;
import vn.cxn.apache_camel.service.RouteVersionService;
import vn.cxn.apache_camel.service.ServiceManagementService;
import vn.cxn.apache_camel.validation.MissingComponentsDownloadedException;
import vn.cxn.apache_camel.validation.RouteValidationResult;

@Component
public class CamelDashboardMcpTools {

    private static final int MAX_ROUTE_CONTENT_LENGTH = 1_000_000;

    private final ServiceManagementService serviceManagementService;
    private final RouteVersionService routeVersionService;
    private final RouteLifecycleService routeLifecycleService;
    private final RouteQueryService routeQueryService;
    private final RouteValidationService routeValidationService;

    public CamelDashboardMcpTools(
            ServiceManagementService serviceManagementService,
            RouteVersionService routeVersionService,
            RouteLifecycleService routeLifecycleService,
            RouteQueryService routeQueryService,
            RouteValidationService routeValidationService) {
        this.serviceManagementService = serviceManagementService;
        this.routeVersionService = routeVersionService;
        this.routeLifecycleService = routeLifecycleService;
        this.routeQueryService = routeQueryService;
        this.routeValidationService = routeValidationService;
    }

    @Tool(
            name = "services_upsert",
            description = "Create or update an Apache Camel service used to group route versions.")
    public Map<String, Object> upsertService(
            @ToolParam(
                            description =
                                    "Existing service ID. Leave empty to create or match by name.",
                            required = false)
                    String serviceId,
            @ToolParam(description = "Human-readable service name.", required = true) String name,
            @ToolParam(description = "Optional service description.", required = false)
                    String description) {
        ServiceDTO service = serviceManagementService.upsertService(serviceId, name, description);

        return Map.of(
                "serviceId", service.getId(),
                "name", service.getName(),
                "description", service.getDescription(),
                "routeIds", service.getRouteIds() != null ? service.getRouteIds() : List.of(),
                "message", "Service is ready");
    }

    @Tool(
            name = "routes_upload_version",
            description =
                    "Upload a YAML route definition (.yaml or .yml) as a new version under an"
                            + " existing service.")
    public Map<String, Object> uploadRouteVersion(
            @ToolParam(
                            description = "Target service ID returned by services_upsert.",
                            required = true)
                    String serviceId,
            @ToolParam(description = "Route file name, for example orders.yaml.", required = true)
                    String fileName,
            @ToolParam(description = "Full YAML route content.", required = true) String content,
            @ToolParam(description = "Optional version description.", required = false)
                    String description) {
        validateRouteUpload(fileName, content);

        // Run FAST validation before upload
        RouteValidationResult validationResult =
                routeValidationService.validate(serviceId, fileName, content, "FAST");
        if (!validationResult.getIsValid()) {
            String errors =
                    validationResult.getErrors().stream()
                            .map(
                                    e ->
                                            "["
                                                    + e.getCode()
                                                    + "] "
                                                    + e.getMessage()
                                                    + (e.getLocation() != null
                                                            ? " at " + e.getLocation()
                                                            : ""))
                            .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Route validation failed: " + errors);
        }

        RouteVersion version =
                routeVersionService.uploadRoute(serviceId, fileName, content, description);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionId", version.getId());
        result.put("serviceId", version.getServiceId());
        result.put("fileName", version.getFileName());
        result.put("version", version.getVersion());
        result.put("routeIds", version.getRouteIds());
        result.put("originalRouteIds", version.getOriginalRouteIds());
        result.put("message", "Route version uploaded");
        return result;
    }

    @Tool(
            name = "routes_validate",
            description =
                    "Validate a Camel route before deploying it to avoid collisions and runtime"
                            + " syntax/dependency errors.")
    public RouteValidationResult validateRoute(
            @ToolParam(description = "Target service ID.", required = true) String serviceId,
            @ToolParam(description = "Route file name (e.g. orders.yaml).", required = true)
                    String fileName,
            @ToolParam(description = "YAML route content to validate.", required = true)
                    String content,
            @ToolParam(
                            description =
                                    "Validation mode: FAST (syntax/conflicts only) or PRE_DEPLOY"
                                            + " (includes runtime dry-run).",
                            required = false)
                    String mode) {
        return routeValidationService.validate(serviceId, fileName, content, mode);
    }

    @Tool(
            name = "routes_deploy_version",
            description = "Deploy a previously uploaded route version into the Camel runtime.")
    public Map<String, Object> deployRouteVersion(
            @ToolParam(description = "Uploaded route version ID.", required = true)
                    String versionId)
            throws Exception {
        try {
            String routeId = routeLifecycleService.deployFromVersion(versionId);
            return Map.of(
                    "versionId", versionId,
                    "routeId", routeId,
                    "message", "Route deployed");
        } catch (MissingComponentsDownloadedException e) {
            return Map.of(
                    "versionId",
                    versionId,
                    "status",
                    "RESTART_REQUIRED",
                    "downloadedArtifacts",
                    e.getDownloadedArtifacts(),
                    "message",
                    e.getMessage(),
                    "action",
                    "New Camel component JARs were downloaded. Restart the application,"
                            + " then re-deploy version "
                            + versionId
                            + ".");
        }
    }

    @Tool(name = "routes_start", description = "Start a deployed Camel route by route ID.")
    public Map<String, Object> startRoute(
            @ToolParam(
                            description = "Managed route ID returned from route upload or deploy.",
                            required = true)
                    String routeId)
            throws Exception {
        routeLifecycleService.startRoute(routeId);
        return Map.of(
                "routeId",
                routeId,
                "status",
                routeQueryService.getRouteStatus(routeId),
                "message",
                "Route started");
    }

    @Tool(
            name = "routes_deploy_and_start",
            description = "Deploy a route version and immediately start the resulting route.")
    public Map<String, Object> deployAndStartRoute(
            @ToolParam(description = "Uploaded route version ID.", required = true)
                    String versionId)
            throws Exception {
        try {
            String routeId = routeLifecycleService.deployFromVersion(versionId);
            routeLifecycleService.startRoute(routeId);
            return Map.of(
                    "versionId",
                    versionId,
                    "routeId",
                    routeId,
                    "status",
                    routeQueryService.getRouteStatus(routeId),
                    "message",
                    "Route deployed and started");
        } catch (MissingComponentsDownloadedException e) {
            return Map.of(
                    "versionId",
                    versionId,
                    "status",
                    "RESTART_REQUIRED",
                    "downloadedArtifacts",
                    e.getDownloadedArtifacts(),
                    "message",
                    e.getMessage(),
                    "action",
                    "New Camel component JARs were downloaded. Restart the application,"
                            + " then re-deploy version "
                            + versionId
                            + ".");
        }
    }

    @Tool(name = "routes_get_status", description = "Get the runtime status of a Camel route.")
    public Map<String, Object> getRouteStatus(
            @ToolParam(description = "Managed route ID.", required = true) String routeId) {
        return Map.of("routeId", routeId, "status", routeQueryService.getRouteStatus(routeId));
    }

    @Tool(
            name = "services_list",
            description = "List all registered Apache Camel services and their mapped route IDs.")
    public List<Map<String, Object>> listServices() {
        return serviceManagementService.getAllServices().stream()
                .map(
                        s ->
                                Map.<String, Object>of(
                                        "serviceId",
                                        s.getId(),
                                        "name",
                                        s.getName(),
                                        "description",
                                        s.getDescription() != null ? s.getDescription() : "",
                                        "routeIds",
                                        s.getRouteIds() != null ? s.getRouteIds() : List.of(),
                                        "createdAt",
                                        s.getCreatedAt() != null ? s.getCreatedAt().toString() : "",
                                        "updatedAt",
                                        s.getUpdatedAt() != null
                                                ? s.getUpdatedAt().toString()
                                                : ""))
                .collect(Collectors.toList());
    }

    @Tool(
            name = "services_delete",
            description =
                    "Delete a service by ID, including stopping and undeploying all its associated"
                            + " routes and versions.")
    public Map<String, Object> deleteService(
            @ToolParam(description = "The ID of the service to delete.", required = true)
                    String serviceId) {
        boolean deleted = serviceManagementService.deleteService(serviceId);
        return Map.<String, Object>of(
                "serviceId",
                serviceId,
                "deleted",
                deleted,
                "message",
                deleted ? "Service deleted successfully" : "Service not found or failed to delete");
    }

    @Tool(
            name = "routes_list",
            description = "List all currently deployed and active Camel routes in the runtime.")
    public List<Map<String, Object>> listRoutes() {
        return routeQueryService.listRoutes().stream()
                .map(
                        r ->
                                Map.<String, Object>of(
                                        "routeId",
                                        r.id(),
                                        "originalId",
                                        r.originalId() != null ? r.originalId() : "",
                                        "serviceId",
                                        r.serviceId() != null ? r.serviceId() : "",
                                        "status",
                                        r.status(),
                                        "description",
                                        r.description() != null ? r.description() : "",
                                        "sourceUri",
                                        r.sourceUri() != null ? r.sourceUri() : "",
                                        "activeVersion",
                                        r.activeVersion()))
                .collect(Collectors.toList());
    }

    @Tool(name = "routes_stop", description = "Stop a running Camel route by route ID.")
    public Map<String, Object> stopRoute(
            @ToolParam(description = "Managed route ID.", required = true) String routeId)
            throws Exception {
        routeLifecycleService.stopRoute(routeId);
        return Map.of(
                "routeId",
                routeId,
                "status",
                routeQueryService.getRouteStatus(routeId),
                "message",
                "Route stopped");
    }

    @Tool(name = "routes_suspend", description = "Suspend a running Camel route by route ID.")
    public Map<String, Object> suspendRoute(
            @ToolParam(description = "Managed route ID.", required = true) String routeId)
            throws Exception {
        routeLifecycleService.suspendRoute(routeId);
        return Map.of(
                "routeId",
                routeId,
                "status",
                routeQueryService.getRouteStatus(routeId),
                "message",
                "Route suspended");
    }

    @Tool(name = "routes_resume", description = "Resume a suspended Camel route by route ID.")
    public Map<String, Object> resumeRoute(
            @ToolParam(description = "Managed route ID.", required = true) String routeId)
            throws Exception {
        routeLifecycleService.resumeRoute(routeId);
        return Map.of(
                "routeId",
                routeId,
                "status",
                routeQueryService.getRouteStatus(routeId),
                "message",
                "Route resumed");
    }

    @Tool(
            name = "routes_undeploy",
            description = "Undeploy (remove) a stopped Camel route from the runtime.")
    public Map<String, Object> undeployRoute(
            @ToolParam(description = "Managed route ID.", required = true) String routeId)
            throws Exception {
        boolean removed = routeLifecycleService.removeRoute(routeId);
        return Map.<String, Object>of(
                "routeId",
                routeId,
                "removed",
                removed,
                "message",
                removed
                        ? "Route undeployed successfully"
                        : "Route not found or failed to undeploy");
    }

    @Tool(
            name = "routes_list_versions",
            description = "List all uploaded route versions under a specific service.")
    public List<Map<String, Object>> listRouteVersions(
            @ToolParam(description = "Target service ID.", required = true) String serviceId) {
        return routeVersionService.getVersionsByServiceId(serviceId).stream()
                .map(
                        v ->
                                Map.<String, Object>of(
                                        "versionId",
                                        v.getId(),
                                        "fileName",
                                        v.getFileName(),
                                        "version",
                                        v.getVersion(),
                                        "description",
                                        v.getDescription() != null ? v.getDescription() : "",
                                        "autoRestore",
                                        v.isAutoRestore(),
                                        "uploadedAt",
                                        v.getUploadedAt() != null
                                                ? v.getUploadedAt().toString()
                                                : "",
                                        "deployedAt",
                                        v.getDeployedAt() != null
                                                ? v.getDeployedAt().toString()
                                                : "",
                                        "routeIds",
                                        v.getRouteIds() != null ? v.getRouteIds() : List.of()))
                .collect(Collectors.toList());
    }

    @Tool(name = "routes_delete_version", description = "Delete an uploaded route version by ID.")
    public Map<String, Object> deleteRouteVersion(
            @ToolParam(description = "Uploaded route version ID.", required = true)
                    String versionId) {
        boolean deleted = routeVersionService.deleteVersion(versionId);
        return Map.<String, Object>of(
                "versionId",
                versionId,
                "deleted",
                deleted,
                "message",
                deleted
                        ? "Route version deleted successfully"
                        : "Route version not found or failed to delete");
    }

    @Tool(
            name = "routes_set_auto_restore",
            description = "Toggle the auto-restore status of a specific route version.")
    public Map<String, Object> setAutoRestore(
            @ToolParam(description = "Uploaded route version ID.", required = true)
                    String versionId,
            @ToolParam(description = "Enable or disable auto-restore.", required = true)
                    boolean autoRestore) {
        boolean updated = routeVersionService.updateAutoRestoreStatus(versionId, autoRestore);
        return Map.<String, Object>of(
                "versionId",
                versionId,
                "autoRestore",
                autoRestore,
                "updated",
                updated,
                "message",
                updated
                        ? "Auto-restore updated successfully"
                        : "Version not found or failed to update");
    }

    private void validateRouteUpload(String fileName, String content) {
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!normalizedFileName.endsWith(".yaml") && !normalizedFileName.endsWith(".yml")) {
            throw new IllegalArgumentException("Only .yaml or .yml files are allowed");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Route content must not be empty");
        }
        if (content.length() > MAX_ROUTE_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Route content exceeds max allowed size");
        }
        validateYamlSyntax(content);
    }

    private void validateYamlSyntax(String content) {
        try {
            new Yaml().loadAll(content).forEach(document -> {});
        } catch (YAMLException e) {
            throw new IllegalArgumentException("YAML syntax error: " + e.getMessage(), e);
        }
    }
}
