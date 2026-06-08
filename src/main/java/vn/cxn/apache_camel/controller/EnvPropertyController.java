package vn.cxn.apache_camel.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.cxn.apache_camel.model.dto.EnvProperty;
import vn.cxn.apache_camel.service.EnvPropertyService;

@RestController
@RequestMapping("/api/env-properties")
@CrossOrigin(origins = "*") // Adjust origin as needed for your Vue app
public class EnvPropertyController {

    private final EnvPropertyService envPropertyService;

    public EnvPropertyController(EnvPropertyService envPropertyService) {
        this.envPropertyService = envPropertyService;
    }

    @GetMapping
    public ResponseEntity<List<EnvProperty>> getAll() {
        List<EnvProperty> all = envPropertyService.getAll();
        // Deep clone and mask secret values for security
        List<EnvProperty> maskedList =
                all.stream()
                        .map(
                                prop -> {
                                    EnvProperty masked = new EnvProperty();
                                    masked.setKey(prop.getKey());
                                    masked.setDescription(prop.getDescription());
                                    masked.setUpdatedAt(prop.getUpdatedAt());
                                    masked.setSecret(prop.isSecret());
                                    if (prop.isSecret()) {
                                        masked.setValue("********");
                                    } else {
                                        masked.setValue(prop.getValue());
                                    }
                                    return masked;
                                })
                        .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(maskedList);
    }

    @GetMapping("/{key}")
    public ResponseEntity<EnvProperty> getByKey(@PathVariable String key) {
        EnvProperty prop = envPropertyService.get(key);
        if (prop != null) {
            // Clone to avoid modifying the reference in memory map
            EnvProperty masked = new EnvProperty();
            masked.setKey(prop.getKey());
            masked.setDescription(prop.getDescription());
            masked.setUpdatedAt(prop.getUpdatedAt());
            masked.setSecret(prop.isSecret());

            if (prop.isSecret()) {
                masked.setValue("********");
            } else {
                masked.setValue(prop.getValue());
            }
            return ResponseEntity.ok(masked);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<EnvProperty> save(@RequestBody EnvProperty property) {
        if (property.getKey() == null || property.getKey().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        EnvProperty saved = envPropertyService.save(property);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        if (envPropertyService.delete(key)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
