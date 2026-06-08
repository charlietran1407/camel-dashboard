package vn.cxn.apache_camel.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.cxn.apache_camel.service.ClusterNodeService;

@RestController
@RequestMapping("/api/cluster")
@CrossOrigin(origins = "*")
public class ClusterController {

    private final ClusterNodeService clusterNodeService;

    public ClusterController(ClusterNodeService clusterNodeService) {
        this.clusterNodeService = clusterNodeService;
    }

    /** GET /api/cluster/nodes Response list cluster nodes with status and uptime. */
    @GetMapping("/nodes")
    public ResponseEntity<List<Map<String, Object>>> getNodes() {
        List<Map<String, Object>> rs = clusterNodeService.getAllNodes();
        return ResponseEntity.ok(rs);
    }

    /** GET /api/cluster/current Response current node info (self). */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentNode() {
        return clusterNodeService.getAllNodes().stream()
                .filter(n -> Boolean.TRUE.equals(n.get("isCurrent")))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/cluster/nodes/{instanceId} Force remove a node from the cluster registry. Cannot
     * remove the current node.
     */
    @DeleteMapping("/nodes/{instanceId}")
    public ResponseEntity<Map<String, Object>> evictNode(@PathVariable String instanceId) {
        try {
            boolean evicted = clusterNodeService.evictNode(instanceId);
            if (!evicted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("status", "evicted", "instanceId", instanceId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "instanceId", instanceId));
        }
    }
}
