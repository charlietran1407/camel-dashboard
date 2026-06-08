package vn.cxn.apache_camel.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;
import vn.cxn.apache_camel.service.DbConnectionService;

@RestController
@RequestMapping("/api/db-connections")
public class DbConnectionController {

    private final DbConnectionService connectionService;

    public DbConnectionController(DbConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<DbConnectionEntity> getAllConnections() {
        return connectionService.getAllConnections();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DbConnectionEntity> getConnectionById(@PathVariable UUID id) {
        return connectionService
                .getConnectionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public DbConnectionEntity saveConnection(@RequestBody DbConnectionEntity connection) {
        return connectionService.saveConnection(connection);
    }

    @PostMapping("/test")
    public ResponseEntity<ConnectionTestResult> testConnection(
            @RequestBody DbConnectionEntity connection) {
        try {
            connectionService.testConnection(connection);
            return ResponseEntity.ok(new ConnectionTestResult(true, "Connection successful"));
        } catch (Exception e) {
            return ResponseEntity.ok(new ConnectionTestResult(false, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConnection(@PathVariable UUID id) {
        connectionService.deleteConnection(id);
        return ResponseEntity.ok(
                Map.of("success", true, "message", "Connection deleted successfully"));
    }

    public static class ConnectionTestResult {
        private final boolean success;
        private final String message;

        public ConnectionTestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
