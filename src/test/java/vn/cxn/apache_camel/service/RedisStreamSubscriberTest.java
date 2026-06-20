package vn.cxn.apache_camel.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import vn.cxn.apache_camel.service.route_command.ClusterRouteCommand;

class RedisStreamSubscriberTest {

    private RedisStreamSubscriber subscriber;
    private ClusterNodeService clusterNodeService;
    private RedisTemplate<String, Object> redisTemplate;
    private RedisMessageListenerContainer redisMessageListenerContainer;
    private List<ClusterRouteCommand> routeCommands;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        clusterNodeService = mock(ClusterNodeService.class);
        redisTemplate = mock(RedisTemplate.class);
        redisMessageListenerContainer = mock(RedisMessageListenerContainer.class);
        routeCommands = new ArrayList<>();

        subscriber =
                new RedisStreamSubscriber(
                        clusterNodeService,
                        redisTemplate,
                        redisMessageListenerContainer,
                        routeCommands);
    }

    @Test
    void testConsolidateRecords_deDuplicatesEvents() {
        Map<Object, Object> m1 = new HashMap<>();
        m1.put("targetId", "route_A");
        m1.put("eventType", "START_ROUTE");
        MapRecord<String, Object, Object> r1 =
                MapRecord.create("streamKey", m1).withId(RecordId.of("100-0"));

        Map<Object, Object> m2 = new HashMap<>();
        m2.put("targetId", "route_A");
        m2.put("eventType", "STOP_ROUTE");
        MapRecord<String, Object, Object> r2 =
                MapRecord.create("streamKey", m2).withId(RecordId.of("101-0"));

        Map<Object, Object> m3 = new HashMap<>();
        m3.put("targetId", "route_A");
        m3.put("eventType", "START_ROUTE");
        MapRecord<String, Object, Object> r3 =
                MapRecord.create("streamKey", m3).withId(RecordId.of("102-0"));

        Map<Object, Object> m4 = new HashMap<>();
        m4.put("targetId", "route_A");
        m4.put("eventType", "STOP_ROUTE");
        MapRecord<String, Object, Object> r4 =
                MapRecord.create("streamKey", m4).withId(RecordId.of("103-0"));

        Map<Object, Object> m5 = new HashMap<>();
        m5.put("targetId", "route_B");
        m5.put("eventType", "START_ROUTE");
        MapRecord<String, Object, Object> r5 =
                MapRecord.create("streamKey", m5).withId(RecordId.of("104-0"));

        List<MapRecord<String, Object, Object>> records = List.of(r1, r2, r3, r4, r5);

        List<MapRecord<String, Object, Object>> consolidated =
                subscriber.consolidateRecords(records);

        // We expect only 2 records:
        // - route_A final state: STOP_ROUTE (103-0)
        // - route_B final state: START_ROUTE (104-0)
        assertEquals(2, consolidated.size());

        // Verify chronological order is preserved (103-0 before 104-0)
        assertEquals("103-0", consolidated.get(0).getId().getValue());
        assertEquals("route_A", consolidated.get(0).getValue().get("targetId"));
        assertEquals("STOP_ROUTE", consolidated.get(0).getValue().get("eventType"));

        assertEquals("104-0", consolidated.get(1).getId().getValue());
        assertEquals("route_B", consolidated.get(1).getValue().get("targetId"));
        assertEquals("START_ROUTE", consolidated.get(1).getValue().get("eventType"));
    }

    @Test
    void testConsolidateRecords_emptyList() {
        List<MapRecord<String, Object, Object>> consolidated =
                subscriber.consolidateRecords(Collections.emptyList());
        assertTrue(consolidated.isEmpty());
    }

    @Test
    void testConsolidateRecords_nullTargetId() {
        Map<Object, Object> m1 = new HashMap<>();
        m1.put("eventType", "SOME_EVENT");
        MapRecord<String, Object, Object> r1 =
                MapRecord.create("streamKey", m1)
                        .withId(RecordId.of("100-0")); // targetId is missing/null

        List<MapRecord<String, Object, Object>> consolidated =
                subscriber.consolidateRecords(List.of(r1));
        assertEquals(1, consolidated.size());
        assertEquals("100-0", consolidated.get(0).getId().getValue());
    }
}
