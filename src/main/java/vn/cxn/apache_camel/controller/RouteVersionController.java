package vn.cxn.apache_camel.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.RouteValidationService;
import vn.cxn.apache_camel.service.RouteVersionService;
import vn.cxn.apache_camel.service.SystemLogService;
import vn.cxn.apache_camel.validation.RouteValidationResult;
import vn.cxn.apache_camel.validation.ValidationError;

@RestController
@RequestMapping("/api/versions")
public class RouteVersionController {

    private final RouteVersionService versionService;
    private final RouteValidationService validationService;
    private final SystemLogService systemLogService;

    public RouteVersionController(
            RouteVersionService versionService,
            RouteValidationService validationService,
            SystemLogService systemLogService) {
        this.versionService = versionService;
        this.validationService = validationService;
        this.systemLogService = systemLogService;
    }

    /** GET /api/versions - all uploaded versions */
    @GetMapping
    public ResponseEntity<List<RouteVersion>> getAllVersions() {
        List<RouteVersion> list = versionService.getAllVersions();
        return ResponseEntity.ok(list);
    }

    /** GET /api/versions/routes - distinct routeIds */
    @GetMapping("/routes")
    public ResponseEntity<List<String>> getAllRouteIds() {
        return ResponseEntity.ok(versionService.getAllRouteIds());
    }

    /** GET /api/versions/service/{serviceId} - versions for a specific service */
    @GetMapping("/service/{serviceId}")
    public ResponseEntity<List<RouteVersion>> getVersionsForService(
            @PathVariable String serviceId) {
        List<RouteVersion> list = versionService.getVersionsByServiceId(serviceId);
        return ResponseEntity.ok(list);
    }

    /** GET /api/versions/route/{routeId} - versions for a specific route */
    @GetMapping("/route/{routeId}")
    public ResponseEntity<List<RouteVersion>> getVersionsForRoute(@PathVariable String routeId) {
        List<RouteVersion> list = versionService.getVersionsByRouteId(routeId);
        return ResponseEntity.ok(list);
    }

    /** GET /api/versions/{versionId} - single version detail */
    @GetMapping("/{versionId}")
    public ResponseEntity<?> getVersion(@PathVariable String versionId) {
        Optional<RouteVersion> versionOpt = versionService.getVersionById(versionId);
        if (versionOpt.isPresent()) {
            RouteVersion v = versionOpt.get();
            try {
                String content = versionService.getContentFromDisk(v.getId());
                RouteValidationResult valResult =
                        validationService.validate(
                                v.getServiceId(), v.getFileName(), content, "FAST");
                v.setWarnings(valResult.getWarnings());
            } catch (Exception ignored) {
            }
            return ResponseEntity.ok(v);
        }
        return ResponseEntity.status(404).body(Map.of("error", "Version not found"));
    }

    /** GET /api/versions/{versionId}/content - get raw yaml/xml content inside JSON */
    @GetMapping("/{versionId}/content")
    public ResponseEntity<?> getVersionContent(@PathVariable String versionId) {
        try {
            String content = versionService.getContentFromDisk(versionId);
            return ResponseEntity.ok(Map.of("versionId", versionId, "content", content));
        } catch (Exception e) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Content not found: " + e.getMessage()));
        }
    }

    /**
     * POST /api/versions/upload - upload a YAML/XML route file (multipart) Params: file
     * (multipart), description (optional form field)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadRouteFile(
            @RequestParam MultipartFile file,
            @RequestParam String serviceId,
            @RequestParam(required = false, defaultValue = "") String description) {
        try {
            if (file.isEmpty()) {
                systemLogService.log(
                        "UPLOAD",
                        "FAILED",
                        serviceId,
                        "Upload failed: File is empty",
                        null,
                        null,
                        "unknown");
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }
            String fileName = file.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".yaml") && !fileName.endsWith(".yml"))) {
                systemLogService.log(
                        "UPLOAD",
                        "FAILED",
                        serviceId,
                        "Upload failed: Only .yaml or .yml files are allowed",
                        null,
                        null,
                        fileName);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only .yaml or .yml files are allowed"));
            }

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            // Integrate Validation Pipeline (FAST mode)
            RouteValidationResult validationResult =
                    validationService.validate(serviceId, fileName, content, "FAST");
            if (!validationResult.getIsValid()) {
                String errorMsg =
                        "Validation failed: "
                                + validationResult.getErrors().stream()
                                        .map(ValidationError::getMessage)
                                        .collect(Collectors.joining("; "));
                systemLogService.log("UPLOAD", "FAILED", serviceId, errorMsg, null, null, fileName);
                return ResponseEntity.badRequest().body(validationResult);
            }

            RouteVersion version =
                    versionService.uploadRoute(
                            serviceId,
                            fileName,
                            content,
                            description,
                            validationResult.getWarnings());
            return ResponseEntity.ok(version);
        } catch (Exception e) {
            String fileName = file != null ? file.getOriginalFilename() : "unknown";
            systemLogService.log(
                    "UPLOAD",
                    "FAILED",
                    serviceId,
                    "Upload failed: " + e.getMessage(),
                    null,
                    null,
                    fileName);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /** POST /api/versions/upload/text - upload route as raw YAML text body */
    @PostMapping("/upload/text")
    public ResponseEntity<?> uploadRouteText(
            @RequestParam String serviceId,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestBody String content) {
        String fileName = java.util.UUID.randomUUID().toString() + ".yaml";
        try {
            // Integrate Validation Pipeline (FAST mode)
            RouteValidationResult validationResult =
                    validationService.validate(serviceId, fileName, content, "FAST");
            if (!validationResult.getIsValid()) {
                String errorMsg =
                        "Validation failed: "
                                + validationResult.getErrors().stream()
                                        .map(ValidationError::getMessage)
                                        .collect(Collectors.joining("; "));
                systemLogService.log("UPLOAD", "FAILED", serviceId, errorMsg, null, null, fileName);
                return ResponseEntity.badRequest().body(validationResult);
            }

            RouteVersion version =
                    versionService.uploadRoute(
                            serviceId,
                            fileName,
                            content,
                            description,
                            validationResult.getWarnings());
            return ResponseEntity.ok(version);
        } catch (Exception e) {
            systemLogService.log(
                    "UPLOAD",
                    "FAILED",
                    serviceId,
                    "Upload failed: " + e.getMessage(),
                    null,
                    null,
                    fileName);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /** DELETE /api/versions/{versionId} */
    @DeleteMapping("/{versionId}")
    public ResponseEntity<Map<String, Object>> deleteVersion(@PathVariable String versionId) {
        boolean deleted = versionService.deleteVersion(versionId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("versionId", versionId, "deleted", true));
        } else {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Version not found: " + versionId));
        }
    }

    /**
     * POST /api/versions/{versionId}/auto-restore?autoRestore=true|false - toggle auto-restore
     * status
     */
    @PostMapping("/{versionId}/auto-restore")
    public ResponseEntity<Map<String, Object>> updateAutoRestoreStatus(
            @PathVariable String versionId, @RequestParam boolean autoRestore) {
        boolean updated = versionService.updateAutoRestoreStatus(versionId, autoRestore);
        if (updated) {
            return ResponseEntity.ok(
                    Map.of(
                            "versionId",
                            versionId,
                            "autoRestore",
                            autoRestore,
                            "message",
                            "Auto-restore status updated"));
        } else {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Version not found: " + versionId));
        }
    }
}
