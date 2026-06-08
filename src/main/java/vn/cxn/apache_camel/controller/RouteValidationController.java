package vn.cxn.apache_camel.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.cxn.apache_camel.service.RouteValidationService;
import vn.cxn.apache_camel.validation.RouteValidationResult;

@RestController
@RequestMapping("/api/routes")
public class RouteValidationController {

    private final RouteValidationService validationService;

    public RouteValidationController(RouteValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * POST /api/routes/validate Accepts a JSON body containing serviceId, fileName, content, and
     * optional validation mode.
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateRoute(@RequestBody ValidationRequest request) {
        if (request.getServiceId() == null || request.getServiceId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "serviceId is required"));
        }
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileName is required"));
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }

        try {
            RouteValidationResult result =
                    validationService.validate(
                            request.getServiceId(),
                            request.getFileName(),
                            request.getContent(),
                            request.getMode());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Validation failed: " + e.getMessage()));
        }
    }

    public static class ValidationRequest {
        private String serviceId;
        private String fileName;
        private String content;
        private String mode; // FAST or PRE_DEPLOY

        public ValidationRequest() {}

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }
}
