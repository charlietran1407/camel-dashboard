package vn.cxn.apache_camel.model.dto;

import java.io.Serializable;
import java.util.Map;
import vn.cxn.apache_camel.model.enums.ClusterEventType;

public class ClusterEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId; // Redis Stream message ID
    private ClusterEventType eventType;
    private String targetId; // routeId or versionId
    private String initiatorId; // instanceId of the node that created the event
    private long timestamp;
    private Map<String, String>
            metadata; // Extra metadata e.g. deleted route IDs for DELETE_VERSION

    public ClusterEvent() {}

    public ClusterEvent(
            String eventId,
            ClusterEventType eventType,
            String targetId,
            String initiatorId,
            long timestamp,
            Map<String, String> metadata) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.targetId = targetId;
        this.initiatorId = initiatorId;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public ClusterEventType getEventType() {
        return eventType;
    }

    public void setEventType(ClusterEventType eventType) {
        this.eventType = eventType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(String initiatorId) {
        this.initiatorId = initiatorId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "ClusterEvent{"
                + "eventId='"
                + eventId
                + '\''
                + ", eventType="
                + eventType
                + ", targetId='"
                + targetId
                + '\''
                + ", initiatorId='"
                + initiatorId
                + '\''
                + ", timestamp="
                + timestamp
                + ", metadata="
                + metadata
                + '}';
    }
}
