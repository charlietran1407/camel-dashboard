package vn.cxn.apache_camel.model.dto;

import java.time.Instant;

public record RouteRuntimeState(
        String routeId,
        String instanceId,
        String currentState,
        String errorMessage,
        Instant lastUpdated) {
    public RouteRuntimeState {
        if (currentState == null) {
            currentState = "UNKNOWN";
        }
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }
}
