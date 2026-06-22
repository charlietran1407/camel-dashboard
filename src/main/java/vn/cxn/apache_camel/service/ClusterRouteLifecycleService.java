package vn.cxn.apache_camel.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.config.RedisClusterProperties;
import vn.cxn.apache_camel.model.enums.ClusterEventType;

@Service
@Primary
public class ClusterRouteLifecycleService implements RouteLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ClusterRouteLifecycleService.class);

    private final CamelRouteService delegate;
    private final ClusterNodeService clusterNodeService;
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final RedisClusterProperties properties;

    @Value("${camel.dashboard.cluster.stream-key:cluster:event:stream}")
    private String streamKey;

    @Value("${camel.dashboard.cluster.channel:cluster:event:channel}")
    private String channel;

    public ClusterRouteLifecycleService(
            CamelRouteService delegate,
            ClusterNodeService clusterNodeService,
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
            RedisClusterProperties properties) {
        this.delegate = delegate;
        this.clusterNodeService = clusterNodeService;
        this.redisTemplateProvider = redisTemplateProvider;
        this.properties = properties;
    }

    @Override
    public void startRoute(String routeId) throws Exception {
        delegate.startRoute(routeId);
        if (clusterNodeService.isRedisEnabled()) {
            publishEvent(ClusterEventType.START_ROUTE, routeId, null);
        }
    }

    @Override
    public void stopRoute(String routeId) throws Exception {
        delegate.stopRoute(routeId);
        if (clusterNodeService.isRedisEnabled()) {
            publishEvent(ClusterEventType.STOP_ROUTE, routeId, null);
        }
    }

    @Override
    public void suspendRoute(String routeId) throws Exception {
        delegate.suspendRoute(routeId);
        if (clusterNodeService.isRedisEnabled()) {
            publishEvent(ClusterEventType.SUSPEND_ROUTE, routeId, null);
        }
    }

    @Override
    public void resumeRoute(String routeId) throws Exception {
        delegate.resumeRoute(routeId);
        if (clusterNodeService.isRedisEnabled()) {
            publishEvent(ClusterEventType.RESUME_ROUTE, routeId, null);
        }
    }

    @Override
    public boolean removeRoute(String routeId) throws Exception {
        boolean removed = delegate.removeRoute(routeId);
        if (removed && clusterNodeService.isRedisEnabled()) {
            publishEvent(ClusterEventType.REMOVE_ROUTE, routeId, null);
        }
        return removed;
    }

    @Override
    public String deployFromVersion(String versionId) throws Exception {
        String routeId = delegate.deployFromVersion(versionId);
        if (clusterNodeService.isRedisEnabled()) {
            publishEvent(ClusterEventType.DEPLOY_VERSION, versionId, null);
        }
        return routeId;
    }

    @Override
    public void cleanUpRestDefinitions(String serviceId, Collection<String> routeIds) {
        delegate.cleanUpRestDefinitions(serviceId, routeIds);
    }

    @Override
    public void cleanUpRestDefinitions(String routeId) {
        delegate.cleanUpRestDefinitions(routeId);
    }

    private void publishEvent(
            ClusterEventType eventType, String targetId, Map<String, String> metadata) {
        try {
            RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return;
            }

            String instanceId = clusterNodeService.getInstanceId();

            Map<String, String> eventMap = new LinkedHashMap<>();
            eventMap.put("eventType", eventType.getValue());
            eventMap.put("targetId", targetId);
            eventMap.put("initiatorId", instanceId);
            eventMap.put("timestamp", String.valueOf(System.currentTimeMillis()));
            if (metadata != null) {
                metadata.forEach((k, v) -> eventMap.put("meta_" + k, v));
            }

            RecordId recordId =
                    redisTemplate
                            .opsForStream()
                            .add(MapRecord.create(streamKey, eventMap), XAddOptions.maxlen(1000L));

            if (recordId != null) {
                String eventId = recordId.getValue();
                log.info(
                        "ClusterRouteLifecycleService: Broadcasted event '{}' for '{}' (ID: {})",
                        eventType,
                        targetId,
                        eventId);

                // Broadcast alert with message ID
                redisTemplate.convertAndSend(channel, eventId);

                // Checkpoint own executed message
                redisTemplate.opsForValue().set(properties.checkpointKey(instanceId), eventId);
            }
        } catch (Exception e) {
            log.error(
                    "ClusterRouteLifecycleService: Failed to publish and broadcast cluster event",
                    e);
        }
    }
}
