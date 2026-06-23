package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
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
import vn.cxn.apache_camel.config.RedisClusterProperties;
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
    private final RedisClusterProperties properties;

    @Value("${camel.dashboard.cluster.stream-key:cluster:event:stream}")
    private String streamKey;

    @Value("${camel.dashboard.cluster.channel:cluster:event:channel}")
    private String channel;

    public RedisStreamSubscriber(
            ClusterNodeService clusterNodeService,
            @Qualifier("clusterRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer,
            List<ClusterRouteCommand> routeCommands,
            RedisClusterProperties properties) {
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
        this.properties = properties;
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

        // 2. Initialize the cluster subscriber checkpoint in a background thread to prevent
        // blocking Spring startup
        new Thread(this::initializeCheckpoint, "redis-checkpoint-initializer").start();
    }

    private void initializeCheckpoint() {
        log.info("RedisStreamSubscriber: Starting checkpoint initializer thread...");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String instanceId = clusterNodeService.getInstanceId();
                if (instanceId == null) {
                    Thread.sleep(1000);
                    continue;
                }

                log.info("RedisStreamSubscriber: Initializing cluster subscriber checkpoint...");

                // Get the latest event ID in the stream
                String latestId = null;
                List<MapRecord<String, Object, Object>> lastRecord =
                        redisTemplate
                                .opsForStream()
                                .reverseRange(
                                        streamKey,
                                        Range.<String>unbounded(),
                                        org.springframework.data.redis.connection.Limit.limit()
                                                .count(1));
                if (lastRecord != null && !lastRecord.isEmpty()) {
                    latestId = lastRecord.get(0).getId().getValue();
                }

                if (latestId != null && !latestId.isBlank()) {
                    redisTemplate.opsForValue().set(properties.checkpointKey(instanceId), latestId);
                    log.info(
                            "RedisStreamSubscriber: Initialized checkpoint for node '{}' to latest"
                                    + " event ID '{}'",
                            instanceId,
                            latestId);
                } else {
                    redisTemplate.opsForValue().set(properties.checkpointKey(instanceId), "0-0");
                    log.info(
                            "RedisStreamSubscriber: Stream '{}' is empty. Initialized checkpoint"
                                    + " for node '{}' to '0-0'",
                            streamKey,
                            instanceId);
                }
                log.info(
                        "RedisStreamSubscriber: Startup checkpoint initialization complete. Node is"
                                + " ready for real-time listening.");
                break; // Successfully initialized, exit the loop
            } catch (Exception e) {
                log.warn(
                        "RedisStreamSubscriber: Startup checkpoint initialization failed (Redis"
                                + " might be offline). Retrying in 5 seconds... Error: {}",
                        e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void handleRealTimeEvent(String eventId) {
        try {
            String instanceId = clusterNodeService.getInstanceId();
            String checkpoint =
                    (String) redisTemplate.opsForValue().get(properties.checkpointKey(instanceId));

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

            // Fetch the stream records from the checkpoint to catch up
            ReadOffset readOffset =
                    checkpoint != null ? ReadOffset.from(checkpoint) : ReadOffset.from("0-0");
            @SuppressWarnings("unchecked")
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().read(StreamOffset.create(streamKey, readOffset));

            if (records == null || records.isEmpty()) {
                log.warn(
                        "RedisStreamSubscriber: Event alert received for '{}', but no records were"
                                + " found in the stream.",
                        eventId);
                return;
            }

            // Consolidate/Deduplicate records
            List<MapRecord<String, Object, Object>> consolidated = consolidateRecords(records);

            log.info(
                    "RedisStreamSubscriber: Replaying {} consolidated event(s) to catch up",
                    consolidated.size());
            for (MapRecord<String, Object, Object> record : consolidated) {
                String recId = record.getId().getValue();
                Map<Object, Object> eventMap = record.getValue();
                processRecord(recId, eventMap);
            }

        } catch (Exception e) {
            log.error(
                    "RedisStreamSubscriber: Failed to handle real-time event alert: {}",
                    eventId,
                    e);
        }
    }

    List<MapRecord<String, Object, Object>> consolidateRecords(
            List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        // targetId -> latest recordId value
        Map<String, String> latestEventMap = new HashMap<>();
        for (MapRecord<String, Object, Object> record : records) {
            String eventId = record.getId().getValue();
            String targetId = (String) record.getValue().get("targetId");
            if (targetId != null) {
                latestEventMap.put(targetId, eventId);
            }
        }

        // Keep only records that are the latest for their targetId, or have no targetId
        List<MapRecord<String, Object, Object>> consolidated = new ArrayList<>();
        for (MapRecord<String, Object, Object> record : records) {
            String eventId = record.getId().getValue();
            String targetId = (String) record.getValue().get("targetId");
            if (targetId == null || eventId.equals(latestEventMap.get(targetId))) {
                consolidated.add(record);
            }
        }

        log.info(
                "RedisStreamSubscriber: Consolidated {} event(s) down to {} event(s).",
                records.size(),
                consolidated.size());
        return consolidated;
    }

    private void processRecord(String eventId, Map<Object, Object> eventMap) {
        String eventTypeStr = (String) eventMap.get("eventType");
        ClusterEventType eventType = ClusterEventType.fromValue(eventTypeStr);
        String targetId = (String) eventMap.get("targetId");
        String initiatorId = (String) eventMap.get("initiatorId");
        String instanceId = clusterNodeService.getInstanceId();

        // 1. Double-check if the current node was the initiator of this event
        if (instanceId.equals(initiatorId)) {
            log.info(
                    "RedisStreamSubscriber: Skipping execution of '{}' for '{}' since this node was"
                            + " the initiator.",
                    eventType,
                    targetId);
            // Just update local checkpoint
            redisTemplate.opsForValue().set(properties.checkpointKey(instanceId), eventId);
            return;
        }

        log.info(
                "RedisStreamSubscriber: Processing event '{}' (ID: {}) for target '{}' initiated by"
                        + " '{}'",
                eventType,
                eventId,
                targetId,
                initiatorId);

        try {
            ClusterRouteCommand command = routeCommands.get(eventType);
            if (command == null) {
                log.warn("RedisStreamSubscriber: Unknown cluster event type '{}'", eventType);
            } else {
                command.execute(new RouteCommandContext(targetId, eventMap));
            }

            // 2. Successful execution - update the checkpoint in Redis
            redisTemplate.opsForValue().set(properties.checkpointKey(instanceId), eventId);
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
