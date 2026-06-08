package vn.cxn.apache_camel.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.cxn.apache_camel.model.dto.SystemLog;
import vn.cxn.apache_camel.service.SystemLogService;

@RestController
@RequestMapping("/api/logs")
public class SystemLogController {

    private final SystemLogService logService;

    public SystemLogController(SystemLogService logService) {
        this.logService = logService;
    }

    /** GET /api/logs - Retrieve system logs optionally filtered by type (e.g. ?type=DEPLOY) */
    @GetMapping
    public ResponseEntity<List<SystemLog>> getLogs(@RequestParam(required = false) String type) {
        return ResponseEntity.ok(logService.getLogs(type));
    }

    /**
     * DELETE /api/logs - Clear logs of a specific type (e.g. ?type=DEPLOY) or all if not specified
     */
    @DeleteMapping
    public ResponseEntity<?> clearLogs(@RequestParam(required = false) String type) {
        logService.clearLogs(type);
        String msg =
                type != null
                        ? "Logs of type '" + type + "' cleared successfully"
                        : "All logs cleared successfully";
        return ResponseEntity.ok(Map.of("message", msg));
    }
}
