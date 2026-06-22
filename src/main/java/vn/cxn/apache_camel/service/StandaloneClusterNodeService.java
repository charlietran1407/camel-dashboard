package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.dto.RouteRuntimeState;
import vn.cxn.apache_camel.model.enums.RouteState;

@Service
@ConditionalOnProperty(
        name = "camel.dashboard.cluster.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class StandaloneClusterNodeService implements ClusterNodeService {

    private static final Logger log = LoggerFactory.getLogger(StandaloneClusterNodeService.class);

    @Value("${camel.dashboard.cluster.instance-id:#{null}}")
    private String configuredInstanceId;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${camel.dashboard.cluster.group:default}")
    private String groupName;

    private final CamelContext camelContext;

    private String instanceId;
    private String ipAddress;
    private Instant startedAt;

    public StandaloneClusterNodeService(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    @PostConstruct
    public void init() {
        try {
            startedAt = Instant.now();
            InetAddress localHost = InetAddress.getLocalHost();
            ipAddress = localHost.getHostAddress();
            String hostname = localHost.getHostName();

            instanceId =
                    (configuredInstanceId != null && !configuredInstanceId.isBlank())
                            ? configuredInstanceId
                            : hostname + ":" + serverPort;

            log.info(
                    "StandaloneClusterNodeService: Initialized standalone instance '{}' ({}:{}) in"
                            + " group '{}' (pure in-memory, no SQL writes)",
                    instanceId,
                    ipAddress,
                    serverPort,
                    groupName);
        } catch (Exception e) {
            log.error("StandaloneClusterNodeService: Failed to initialize standalone instance", e);
        }
    }

    @Override
    public List<Map<String, Object>> getAllNodes() {
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("instanceId", instanceId);
        dto.put("groupName", groupName);
        dto.put("ipAddress", ipAddress);
        dto.put("port", serverPort);
        dto.put("status", "ONLINE");
        dto.put("lastSeen", Instant.now());
        dto.put("startedAt", startedAt);
        dto.put("isCurrent", true);

        if (startedAt != null) {
            Duration uptime = Duration.between(startedAt, Instant.now());
            dto.put("uptimeSeconds", uptime.getSeconds());
        }

        return List.of(dto);
    }

    @Override
    public boolean evictNode(String targetInstanceId) {
        throw new IllegalArgumentException("Cannot evict nodes in standalone mode.");
    }

    @Override
    public void updateLocalRouteStates() {
        // No-op: Route states are queried dynamically on-demand in standalone mode
    }

    @Override
    public List<RouteRuntimeState> getAllRouteStates() {
        if (camelContext == null) {
            return Collections.emptyList();
        }

        List<RouteRuntimeState> states = new ArrayList<>();
        for (Route route : camelContext.getRoutes()) {
            String routeId = route.getId();
            String status;
            try {
                status = camelContext.getRouteController().getRouteStatus(routeId).name();
            } catch (Exception e) {
                status = RouteState.UNKNOWN.name();
            }

            RouteRuntimeState stateEntity =
                    new RouteRuntimeState(routeId, instanceId, status, null, Instant.now());

            states.add(stateEntity);
        }
        return states;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean isRedisEnabled() {
        return false;
    }
}
