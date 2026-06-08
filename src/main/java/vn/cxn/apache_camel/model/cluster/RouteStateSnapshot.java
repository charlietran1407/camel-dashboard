package vn.cxn.apache_camel.model.cluster;

public record RouteStateSnapshot(
        String routeId,
        String instanceId,
        String currentState,
        String errorMessage,
        String lastUpdated) {}
