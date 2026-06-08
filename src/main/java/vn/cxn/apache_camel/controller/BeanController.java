package vn.cxn.apache_camel.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.cxn.apache_camel.model.dto.BeanInfo;
import vn.cxn.apache_camel.service.DynamicBeanService;

@RestController
@RequestMapping("/api/beans")
public class BeanController {

    private final DynamicBeanService dynamicBeanService;

    public BeanController(DynamicBeanService dynamicBeanService) {
        this.dynamicBeanService = dynamicBeanService;
    }

    /** GET /api/beans — list all uploaded beans */
    @GetMapping
    public ResponseEntity<List<BeanInfo>> listBeans() {
        return ResponseEntity.ok(dynamicBeanService.getAllBeans());
    }

    /**
     * POST /api/beans/upload — upload a JAR file containing a bean class. Form params: file — the
     * .jar file beanName — registry name (used in YAML: bean ref: "beanName") className —
     * fully-qualified class name to instantiate description — optional
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadBean(
            @RequestParam("file") MultipartFile file,
            @RequestParam("beanName") String beanName,
            @RequestParam("className") String className,
            @RequestParam(value = "description", required = false, defaultValue = "")
                    String description) {
        try {
            if (file.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));

            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.endsWith(".jar")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only .jar files are allowed"));
            }
            if (beanName == null || beanName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "beanName is required"));
            }
            if (className == null || className.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "className is required"));
            }

            BeanInfo info =
                    dynamicBeanService.uploadBean(
                            fileName, file.getBytes(), beanName, className, description);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /** GET /api/beans/{beanId}/classes — list class names found inside the JAR */
    @GetMapping("/{beanId}/classes")
    public ResponseEntity<?> listClasses(@PathVariable String beanId) {
        try {
            List<String> classes = dynamicBeanService.listClassesInJar(beanId);
            return ResponseEntity.ok(classes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /api/beans/{beanId}/register — load JAR and bind bean into Camel Registry */
    @PostMapping("/{beanId}/register")
    public ResponseEntity<?> registerBean(@PathVariable String beanId) {
        try {
            BeanInfo info = dynamicBeanService.registerBean(beanId);
            return ResponseEntity.ok(
                    Map.of(
                            "beanName",
                            info.getBeanName(),
                            "className",
                            info.getClassName(),
                            "registered",
                            true,
                            "message",
                            "Bean '" + info.getBeanName() + "' registered in Camel Registry"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (ClassNotFoundException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "Class not found in JAR: "
                                            + e.getMessage()
                                            + ". Check the className field."));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    /** POST /api/beans/{beanId}/unregister — remove bean from Camel Registry */
    @PostMapping("/{beanId}/unregister")
    public ResponseEntity<?> unregisterBean(@PathVariable String beanId) {
        try {
            BeanInfo info = dynamicBeanService.unregisterBean(beanId);
            return ResponseEntity.ok(
                    Map.of(
                            "beanName",
                            info.getBeanName(),
                            "registered",
                            false,
                            "message",
                            "Bean '" + info.getBeanName() + "' unregistered"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** DELETE /api/beans/{beanId} — delete bean metadata and JAR file */
    @DeleteMapping("/{beanId}")
    public ResponseEntity<?> deleteBean(@PathVariable String beanId) {
        try {
            boolean deleted = dynamicBeanService.deleteBean(beanId);
            if (deleted) return ResponseEntity.ok(Map.of("beanId", beanId, "deleted", true));
            return ResponseEntity.status(404).body(Map.of("error", "Bean not found: " + beanId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
