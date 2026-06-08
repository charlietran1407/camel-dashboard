package vn.cxn.apache_camel.controller;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    private final CamelContext camelContext;

    public HealthController(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /** GET /api/health Check status backend + Apache Camel context. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();

        String camelStatus = camelContext.getStatus().name(); // STARTED, STARTING, STOPPED, ...
        boolean camelRunning = camelContext.getStatus().isStarted();

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSec = uptimeMs / 1000;

        result.put("status", camelRunning ? "UP" : "DOWN");
        result.put("camelStatus", camelStatus);
        result.put("camelVersion", camelContext.getVersion());
        result.put("uptimeSeconds", uptimeSec);
        result.put("routeCount", camelContext.getRoutesSize());

        return ResponseEntity.ok(result);
    }
}
