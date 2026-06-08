package vn.cxn.apache_camel.model.cluster;

import java.time.Instant;

public record NodeMetadata(
        String instanceId,
        String groupName,
        String ipAddress,
        int port,
        String status,
        String startedAt,
        String lastSeen) {
    public static NodeMetadata online(
            String instanceId,
            String groupName,
            String ipAddress,
            int port,
            String status,
            Instant startedAt) {
        return new NodeMetadata(
                instanceId,
                groupName,
                ipAddress,
                port,
                status,
                startedAt.toString(),
                Instant.now().toString());
    }
}
