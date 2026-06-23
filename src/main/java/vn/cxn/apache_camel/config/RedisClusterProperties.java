package vn.cxn.apache_camel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.dashboard.cluster")
public class RedisClusterProperties {

    private String instanceId;
    private String group = "default";
    private long heartbeatIntervalMs = 10000;
    private long offlineThresholdMs = 30000;
    private long evictionThresholdMs = 300000;
    private boolean enabled;
    private String streamKey;
    private String channel;
    private String nodeKeyPrefix = "cluster:node:";
    private String routeStatesKeyPrefix = "cluster:route:states:";
    private String checkpointKeyPrefix = "cluster:checkpoint:";
    private final Redis redis = new Redis();

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getOfflineThresholdMs() {
        return offlineThresholdMs;
    }

    public void setOfflineThresholdMs(long offlineThresholdMs) {
        this.offlineThresholdMs = offlineThresholdMs;
    }

    public long getEvictionThresholdMs() {
        return evictionThresholdMs;
    }

    public void setEvictionThresholdMs(long evictionThresholdMs) {
        this.evictionThresholdMs = evictionThresholdMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getNodeKeyPrefix() {
        return nodeKeyPrefix;
    }

    public void setNodeKeyPrefix(String nodeKeyPrefix) {
        this.nodeKeyPrefix = nodeKeyPrefix;
    }

    public Redis getRedis() {
        return redis;
    }

    public String nodeKey(String instanceId) {
        return nodeKeyPrefix + instanceId;
    }

    public String getRouteStatesKeyPrefix() {
        return routeStatesKeyPrefix;
    }

    public void setRouteStatesKeyPrefix(String routeStatesKeyPrefix) {
        this.routeStatesKeyPrefix = routeStatesKeyPrefix;
    }

    public String routeStatesKey(String instanceId) {
        return routeStatesKeyPrefix + instanceId;
    }

    public String getCheckpointKeyPrefix() {
        return checkpointKeyPrefix;
    }

    public void setCheckpointKeyPrefix(String checkpointKeyPrefix) {
        this.checkpointKeyPrefix = checkpointKeyPrefix;
    }

    public String checkpointKey(String instanceId) {
        return checkpointKeyPrefix + instanceId;
    }

    public String nodeKeyPattern() {
        return nodeKeyPrefix + "*";
    }

    public String routeStatesKeyPattern() {
        return routeStatesKeyPrefix + "*";
    }

    public static class Redis {
        private int nodeTtlSeconds = 10;
        private String keyspaceNotifications = "Ex";

        public int getNodeTtlSeconds() {
            return nodeTtlSeconds;
        }

        public void setNodeTtlSeconds(int nodeTtlSeconds) {
            this.nodeTtlSeconds = nodeTtlSeconds;
        }

        public String getKeyspaceNotifications() {
            return keyspaceNotifications;
        }

        public void setKeyspaceNotifications(String keyspaceNotifications) {
            this.keyspaceNotifications = keyspaceNotifications;
        }
    }
}
