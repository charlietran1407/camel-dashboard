package vn.cxn.apache_camel.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.cxn.apache_camel.model.dto.ServiceDTO;
import vn.cxn.apache_camel.service.ServiceManagementService;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceManagementService serviceManagementService;

    public ServiceController(ServiceManagementService serviceManagementService) {
        this.serviceManagementService = serviceManagementService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceDTO>> getAllServices() {
        return ResponseEntity.ok(serviceManagementService.getAllServices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getService(@PathVariable String id) {
        return serviceManagementService
                .getServiceById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(
                        () ->
                                ResponseEntity.status(404)
                                        .body(Map.of("error", "Service not found")));
    }

    @PostMapping
    public ResponseEntity<ServiceDTO> createService(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String description = payload.getOrDefault("description", "");
        return ResponseEntity.ok(serviceManagementService.createService(name, description));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceDTO> updateService(
            @PathVariable String id, @RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String description = payload.getOrDefault("description", "");
        return ResponseEntity.ok(serviceManagementService.upsertService(id, name, description));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteService(@PathVariable String id) {
        try {
            boolean deleted = serviceManagementService.deleteService(id);
            if (deleted) {
                return ResponseEntity.ok(Map.of("id", id, "deleted", true));
            } else {
                return ResponseEntity.status(404).body(Map.of("error", "Service not found"));
            }
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
