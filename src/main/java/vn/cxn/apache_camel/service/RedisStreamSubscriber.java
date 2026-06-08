package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.enums.ClusterEventType;
import vn.cxn.apache_camel.service.route_command.ClusterRouteCommand;
import vn.cxn.apache_camel.service.route_command.RouteCommandContext;

@Service
@ConditionalOnProperty(name = "camel.dashboard.cluster.enabled", havingValue = "true")
public class RedisStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamSubscriber.class);

    private final ClusterNodeService clusterNodeService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final Map<ClusterEventType, ClusterRouteCommand> routeCommands;

    @Value("${camel.dashboard.cluster.stream-key:cluster:event:stream}")
    private String streamKey;

    @Value("${camel.dashboard.cluster.channel:cluster:event:channel}")
    private String channel;

    public RedisStreamSubscriber(
            ClusterNodeService clusterNodeService,
            @Qualifier("clusterRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer,
            List<ClusterRouteCommand> routeCommands) {
        this.clusterNodeService = clusterNodeService;
        this.redisTemplate = redisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
        this.routeCommands =
                routeCommands.stream()
                        .collect(
                                Collectors.toMap(
                                        ClusterRouteCommand::eventType,
                                        Function.identity(),
                                        (first, second) -> first,
                                        () -> new EnumMap<>(ClusterEventType.class)));
    }

    @PostConstruct
    public void init() {
        // 1. Subscribe to the Pub/Sub alert channel first so we start receiving
        // real-time alerts
        redisMessageListenerContainer.addMessageListener(
                (message, pattern) -> {
                    try {
                        String eventId = new String(message.getBody());
                        // Strip quotes from Jackson string serialization
                        if (eventId.startsWith("\"") && eventId.endsWith("\"")) {
                            eventId = eventId.substring(1, eventId.length() - 1);
                        }
                        log.info(
                                "RedisStreamSubscriber: [Alert Received] Real-time event alert on"
                                        + " Pub/Sub: {}",
                                eventId);
                        handleRealTimeEvent(eventId);
                    } catch (Exception e) {
                        log.error("RedisStreamSubscriber: Failed to process Pub/Sub alert", e);
                    }
                },
                new PatternTopic(channel));

        // 2. Execute the recovery/catch-up flow in a background thread to prevent
        // blocking Spring startup
        new Thread(this::recoverAndCatchUp, "redis-cluster-recovery-thread").start();
    }

    private void recoverAndCatchUp() {
        try {
            log.info(
                    "RedisStreamSubscriber: Initiating cluster recovery and historical catch-up"
                            + " flow...");
            String instanceId = clusterNodeService.getInstanceId();
            String checkpoint =
                    (String) redisTemplate.opsForValue().get("cluster:checkpoint:" + instanceId);

            ReadOffset readOffset;
            if (checkpoint == null || checkpoint.isBlank()) {
                log.info(
                        "RedisStreamSubscriber: No checkpoint found for node '{}'. Replaying from"
                                + " beginning (0-0)...",
                        instanceId);
                readOffset = ReadOffset.from("0-0");
            } else {
                log.info(
                        "RedisStreamSubscriber: Found checkpoint '{}' for node '{}'. Replaying"
                                + " events after this offset...",
                        checkpoint,
                        instanceId);
                readOffset = ReadOffset.from(checkpoint);
            }

            // Fetch all stream records after the offset
            @SuppressWarnings("unchecked")
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().read(StreamOffset.create(streamKey, readOffset));

            if (records != null && !records.isEmpty()) {
                log.info(
                        "RedisStreamSubscriber: Found {} historical event(s) to catch up",
                        records.size());
                for (MapRecord<String, Object, Object> record : records) {
                    String eventId = record.getId().getValue();
                    Map<Object, Object> eventMap = record.getValue();
                    processRecord(eventId, eventMap, true);
                }
                log.info(
                        "RedisStreamSubscriber: Replayed all {} historical event(s) successfully.",
                        records.size());
            } else {
                log.info(
                        "RedisStreamSubscriber: No historical events found. Node is fully caught up"
                                + " with the cluster.");
            }
            log.info(
                    "RedisStreamSubscriber: Recovery flow complete. Successfully transitioned to"
                            + " real-time listening mode.");
        } catch (Exception e) {
            log.error("RedisStreamSubscriber: Recovery flow failed", e);
        }
    }

    private void handleRealTimeEvent(String eventId) {
        try {
            String instanceId = clusterNodeService.getInstanceId();
            String checkpoint =
                    (String) redisTemplate.opsForValue().get("cluster:checkpoint:" + instanceId);

            // Ignore real-time alerts if they have already been processed (i.e. older or
            // equal to checkpoint)
            if (checkpoint != null && !isNewerThanCheckpoint(eventId, checkpoint)) {
                log.debug(
                        "RedisStreamSubscriber: Ignoring real-time alert '{}' as it is already"
                                + " processed (checkpoint: {})",
                        eventId,
                        checkpoint);
                return;
            }

            // Fetch the specific event details from the stream
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().range(streamKey, Range.closed(eventId, eventId));

            if (records == null || records.isEmpty()) {
                log.warn(
                        "RedisStreamSubscriber: Event alert received for '{}', but no record was"
                                + " found in the stream.",
                        eventId);
                return;
            }

            MapRecord<String, Object, Object> record = records.get(0);
            processRecord(record.getId().getValue(), record.getValue(), false);

        } catch (Exception e) {
            log.error(
                    "RedisStreamSubscriber: Failed to handle real-time event alert: {}",
                    eventId,
                    e);
        }
    }

    private void processRecord(String eventId, Map<Object, Object> eventMap, boolean isRecovery) {
        String eventTypeStr = (String) eventMap.get("eventType");
        ClusterEventType eventType = ClusterEventType.fromValue(eventTypeStr);
        String targetId = (String) eventMap.get("targetId");
        String initiatorId = (String) eventMap.get("initiatorId");
        String instanceId = clusterNodeService.getInstanceId();

        // 1. Double-check if the current node was the initiator of this event (in
        // non-recovery mode)
        if (!isRecovery && instanceId.equals(initiatorId)) {
            log.info(
                    "RedisStreamSubscriber: Skipping execution of '{}' for '{}' since this node was"
                            + " the initiator.",
                    eventType,
                    targetId);
            // Just update local checkpoint
            redisTemplate.opsForValue().set("cluster:checkpoint:" + instanceId, eventId);
            return;
        }

        log.info(
                "RedisStreamSubscriber: Processing event '{}' (ID: {}) for target '{}' initiated by"
                        + " '{}'{}",
                eventType,
                eventId,
                targetId,
                initiatorId,
                isRecovery ? " [REPLAY]" : "");

        try {
            ClusterRouteCommand command = routeCommands.get(eventType);
            if (command == null) {
                log.warn("RedisStreamSubscriber: Unknown cluster event type '{}'", eventType);
            } else {
                command.execute(new RouteCommandContext(targetId, eventMap));
            }

            // 2. Successful execution - update the checkpoint in Redis
            redisTemplate.opsForValue().set("cluster:checkpoint:" + instanceId, eventId);
            log.info(
                    "RedisStreamSubscriber: Successfully processed event '{}' (ID: {}). Checkpoint"
                            + " updated.",
                    eventType,
                    eventId);

        } catch (Exception e) {
            log.error(
                    "RedisStreamSubscriber: Failed to process event '{}' (ID: {}) for target '{}':"
                            + " {}",
                    eventType,
                    eventId,
                    targetId,
                    e.getMessage(),
                    e);
        }
    }

    private boolean isNewerThanCheckpoint(String eventId, String checkpoint) {
        if (checkpoint == null || checkpoint.isBlank()) {
            return true;
        }
        String[] eventParts = eventId.split("-");
        String[] checkpointParts = checkpoint.split("-");
        long eventTime = Long.parseLong(eventParts[0]);
        long checkpointTime = Long.parseLong(checkpointParts[0]);
        if (eventTime != checkpointTime) {
            return eventTime > checkpointTime;
        }
        int eventSeq = Integer.parseInt(eventParts[1]);
        int checkpointSeq = Integer.parseInt(checkpointParts[1]);
        return eventSeq > checkpointSeq;
    }
}
