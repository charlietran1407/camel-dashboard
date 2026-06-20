package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.config.RedisClusterProperties;
import vn.cxn.apache_camel.model.cluster.NodeMetadata;
import vn.cxn.apache_camel.model.cluster.RouteStateSnapshot;
import vn.cxn.apache_camel.model.entity.RouteRuntimeStateEntity;
import vn.cxn.apache_camel.model.enums.RouteState;

@Service
@ConditionalOnProperty(prefix = "camel.dashboard.cluster", name = "enabled", havingValue = "true")
public class RedisClusterNodeService implements ClusterNodeService {

    private static final Logger log = LoggerFactory.getLogger(RedisClusterNodeService.class);

    // ---- Internal constants (not configuration) ----
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String EXPIRED_TOPIC = "__keyevent@*__:expired";
    private static final String DELETED_TOPIC = "__keyevent@*__:del";

    // ---- Dependencies ----
    private final RedisTemplate<String, Object> redisTemplate;
    private final CamelContext camelContext;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final RedisClusterProperties properties;

    // server.port is a standard Spring Boot property — keep it as @Value
    @Value("${server.port:8080}")
    private int serverPort;

    // ---- Runtime state ----
    private String instanceId;
    private String ipAddress;
    private Instant startedAt;

    public RedisClusterNodeService(
            @Qualifier("clusterRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            CamelContext camelContext,
            RedisMessageListenerContainer redisMessageListenerContainer,
            RedisClusterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.camelContext = camelContext;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
        this.properties = properties;
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    @PostConstruct
    public void init() {
        try {
            this.startedAt = Instant.now();
            resolveNodeIdentity();
        } catch (Exception e) {
            log.error("RedisClusterNodeService: Failed to resolve node identity", e);
            return;
        }

        // Register keyspace listeners locally (does not connect to Redis)
        try {
            registerKeyspaceListeners();
        } catch (Exception e) {
            log.error("RedisClusterNodeService: Failed to register keyspace listeners locally", e);
        }

        // Attempt initial connection actions gracefully (will retry via heartbeat if Redis is down)
        try {
            configureKeyspaceNotifications();
            registerNode();
            log.info(
                    "RedisClusterNodeService: Registered node '{}' ({}:{}) as ONLINE in group '{}'",
                    instanceId,
                    ipAddress,
                    serverPort,
                    properties.getGroup());
        } catch (Exception e) {
            log.warn(
                    "RedisClusterNodeService: Failed to initialize Redis connection on startup"
                            + " (will retry via heartbeat): {}",
                    e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            redisTemplate.delete(properties.nodeKey(instanceId));
            redisTemplate.delete(properties.routeStatesKey(instanceId));
            log.info(
                    "RedisClusterNodeService: Node '{}' removed from Redis cleanly on shutdown",
                    instanceId);
        } catch (Exception e) {
            log.warn(
                    "RedisClusterNodeService: Failed to remove node from Redis on shutdown: {}",
                    e.getMessage());
        }
    }

    // =====================================================================
    // Init helpers
    // =====================================================================

    private void resolveNodeIdentity() throws java.net.UnknownHostException {
        InetAddress localHost = InetAddress.getLocalHost();
        this.ipAddress = localHost.getHostAddress();
        String hostname = localHost.getHostName();

        String configured = properties.getInstanceId();
        this.instanceId =
                (configured != null && !configured.isBlank())
                        ? configured
                        : hostname + ":" + serverPort;
    }

    private void configureKeyspaceNotifications() {
        String events = properties.getRedis().getKeyspaceNotifications();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().setConfig("notify-keyspace-events", events);
            log.info("RedisClusterNodeService: Configured keyspace notifications: '{}'", events);
        } catch (Exception e) {
            log.warn(
                    "RedisClusterNodeService: Could not configure keyspace notifications ({}). "
                            + "Make sure '{}' events are enabled on the Redis server.",
                    e.getMessage(),
                    events);
        }
    }

    /** One wrapper handles the try/catch + body-decoding, so individual handlers stay focused. */
    private void registerKeyspaceListeners() {
        addKeyspaceListener(EXPIRED_TOPIC, this::onNodeExpired);
        addKeyspaceListener(DELETED_TOPIC, this::onNodeDeleted);
    }

    private void addKeyspaceListener(String pattern, Consumer<String> bodyHandler) {
        redisMessageListenerContainer.addMessageListener(
                (Message message, byte[] patternBytes) -> {
                    try {
                        bodyHandler.accept(new String(message.getBody()));
                    } catch (Exception e) {
                        log.error(
                                "RedisClusterNodeService: Error in keyspace listener '{}'",
                                pattern,
                                e);
                    }
                },
                new PatternTopic(pattern));
    }

    private void onNodeExpired(String key) {
        String nodeId = extractNodeId(key);
        if (nodeId != null) {
            log.warn(
                    "RedisClusterNodeService: [Failure Detection] Node expired in Redis: '{}'",
                    nodeId);
        }
    }

    private void onNodeDeleted(String key) {
        String nodeId = extractNodeId(key);
        if (nodeId != null) {
            log.info("RedisClusterNodeService: Node cleanly removed from Redis: '{}'", nodeId);
        }
    }

    private String extractNodeId(String key) {
        String prefix = properties.getNodeKeyPrefix();
        return (key != null && key.startsWith(prefix)) ? key.substring(prefix.length()) : null;
    }

    // =====================================================================
    // Heartbeat
    // =====================================================================

    @Scheduled(fixedRateString = "${camel.dashboard.cluster.heartbeat-interval-ms:3000}")
    public void heartbeat() {
        try {
            // If the listener container is not running, try to start it
            if (!redisMessageListenerContainer.isRunning()) {
                try {
                    log.info(
                            "RedisClusterNodeService: RedisMessageListenerContainer is not running."
                                    + " Attempting to start...");
                    configureKeyspaceNotifications();
                    redisMessageListenerContainer.start();
                    log.info(
                            "RedisClusterNodeService: RedisMessageListenerContainer started"
                                    + " successfully.");
                } catch (Exception e) {
                    log.warn(
                            "RedisClusterNodeService: Failed to start RedisMessageListenerContainer"
                                    + " (Redis might be offline): {}",
                            e.getMessage());
                }
            }

            registerNode();
            updateLocalRouteStates();
            log.debug(
                    "RedisClusterNodeService: Renewed heartbeat and route states for node '{}'",
                    instanceId);
        } catch (Exception e) {
            log.error("RedisClusterNodeService: Heartbeat renewal failed: {}", e.getMessage());
        }
    }

    // =====================================================================
    // Node registry
    // =====================================================================

    private void registerNode() {
        NodeMetadata metadata =
                NodeMetadata.online(
                        instanceId,
                        properties.getGroup(),
                        ipAddress,
                        serverPort,
                        STATUS_ONLINE,
                        startedAt);
        Duration ttl = Duration.ofSeconds(properties.getRedis().getNodeTtlSeconds());
        redisTemplate.opsForValue().set(properties.nodeKey(instanceId), metadata, ttl);
    }

    @Override
    public List<Map<String, Object>> getAllNodes() {
        try {
            Set<String> keys = redisTemplate.keys(properties.nodeKeyPattern());
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }

            List<Object> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Collections.emptyList();
            }

            return values.stream()
                    .filter(v -> v instanceof NodeMetadata || v instanceof Map)
                    .map(
                            v -> {
                                if (v instanceof NodeMetadata metadata) {
                                    Map<String, Object> map = new LinkedHashMap<>();
                                    map.put("instanceId", metadata.instanceId());
                                    map.put("groupName", metadata.groupName());
                                    map.put("ipAddress", metadata.ipAddress());
                                    map.put("port", metadata.port());
                                    map.put("status", metadata.status());
                                    map.put("startedAt", metadata.startedAt());
                                    map.put("lastSeen", metadata.lastSeen());
                                    return enrichNode(map);
                                } else {
                                    return enrichNode((Map<?, ?>) v);
                                }
                            })
                    .toList();
        } catch (Exception e) {
            log.error("RedisClusterNodeService: Failed to retrieve cluster nodes", e);
            return Collections.emptyList();
        }
    }

    /** Add `isCurrent` and computed `uptimeSeconds` to a raw map received from Redis. */
    private Map<String, Object> enrichNode(Map<?, ?> rawMap) {
        Map<String, Object> dto = new LinkedHashMap<>();
        rawMap.forEach((k, v) -> dto.put(String.valueOf(k), v));

        String nodeId = (String) dto.get("instanceId");
        dto.put("isCurrent", instanceId.equals(nodeId));

        String startStr = (String) dto.get("startedAt");
        if (startStr != null) {
            Instant start = Instant.parse(startStr);
            dto.put("uptimeSeconds", Duration.between(start, Instant.now()).getSeconds());
        }
        return dto;
    }

    @Override
    public boolean evictNode(String targetInstanceId) {
        if (instanceId.equals(targetInstanceId)) {
            throw new IllegalArgumentException(
                    "Cannot evict the current node ('" + instanceId + "').");
        }
        Boolean deletedNode = redisTemplate.delete(properties.nodeKey(targetInstanceId));
        Boolean deletedStates = redisTemplate.delete(properties.routeStatesKey(targetInstanceId));
        log.warn(
                "RedisClusterNodeService: Node '{}' manually evicted. Registry: {}, RouteStates:"
                        + " {}",
                targetInstanceId,
                deletedNode,
                deletedStates);
        return Boolean.TRUE.equals(deletedNode);
    }

    // =====================================================================
    // Route states
    // =====================================================================

    @Override
    public void updateLocalRouteStates() {
        if (camelContext == null) {
            return;
        }
        try {
            List<RouteStateSnapshot> states = new ArrayList<>();
            for (Route route : camelContext.getRoutes()) {
                states.add(snapshotOf(route));
            }
            Duration ttl = Duration.ofSeconds(properties.getRedis().getNodeTtlSeconds());
            redisTemplate.opsForValue().set(properties.routeStatesKey(instanceId), states, ttl);
        } catch (Exception e) {
            log.error("RedisClusterNodeService: Failed to update local route states", e);
        }
    }

    private RouteStateSnapshot snapshotOf(Route route) {
        String routeId = route.getId();
        String status;
        try {
            status = camelContext.getRouteController().getRouteStatus(routeId).name();
        } catch (Exception e) {
            status = RouteState.UNKNOWN.name();
        }
        return new RouteStateSnapshot(routeId, instanceId, status, null, Instant.now().toString());
    }

    @Override
    public List<RouteRuntimeStateEntity> getAllRouteStates() {
        try {
            Set<String> keys = redisTemplate.keys(properties.routeStatesKeyPattern());
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }

            List<Object> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Collections.emptyList();
            }

            return values.stream()
                    .filter(List.class::isInstance)
                    .flatMap(v -> ((List<?>) v).stream())
                    .filter(item -> item instanceof RouteStateSnapshot || item instanceof Map)
                    .map(
                            item -> {
                                if (item instanceof RouteStateSnapshot snapshot) {
                                    RouteRuntimeStateEntity entity = new RouteRuntimeStateEntity();
                                    entity.setRouteId(snapshot.routeId());
                                    entity.setInstanceId(snapshot.instanceId());
                                    entity.setCurrentState(snapshot.currentState());
                                    entity.setErrorMessage(snapshot.errorMessage());
                                    if (snapshot.lastUpdated() != null) {
                                        entity.setLastUpdated(
                                                Instant.parse(snapshot.lastUpdated()));
                                    }
                                    return entity;
                                } else {
                                    return toEntity((Map<?, ?>) item);
                                }
                            })
                    .toList();
        } catch (Exception e) {
            log.error("RedisClusterNodeService: Failed to retrieve route runtime states", e);
            return Collections.emptyList();
        }
    }

    private RouteRuntimeStateEntity toEntity(Map<?, ?> raw) {
        RouteRuntimeStateEntity entity = new RouteRuntimeStateEntity();
        entity.setRouteId((String) raw.get("routeId"));
        entity.setInstanceId((String) raw.get("instanceId"));
        entity.setCurrentState((String) raw.get("currentState"));
        entity.setErrorMessage((String) raw.get("errorMessage"));
        String updated = (String) raw.get("lastUpdated");
        if (updated != null) {
            entity.setLastUpdated(Instant.parse(updated));
        }
        return entity;
    }

    // =====================================================================
    // Simple accessors
    // =====================================================================

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean isRedisEnabled() {
        return true;
    }
}
