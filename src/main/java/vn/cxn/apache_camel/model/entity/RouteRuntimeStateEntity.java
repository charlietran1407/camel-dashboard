package vn.cxn.apache_camel.model.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class RouteRuntimeStateEntity {

    private String routeId;

    private String instanceId;

    private String currentState = "UNKNOWN";

    private String errorMessage;

    private Instant lastUpdated = Instant.now();

    public RouteRuntimeStateEntity() {}

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public static class RouteRuntimeStateId implements Serializable {
        private String routeId;
        private String instanceId;

        public RouteRuntimeStateId() {}

        public RouteRuntimeStateId(String routeId, String instanceId) {
            this.routeId = routeId;
            this.instanceId = instanceId;
        }

        public String getRouteId() {
            return routeId;
        }

        public void setRouteId(String routeId) {
            this.routeId = routeId;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RouteRuntimeStateId that = (RouteRuntimeStateId) o;
            return Objects.equals(routeId, that.routeId)
                    && Objects.equals(instanceId, that.instanceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(routeId, instanceId);
        }
    }
}
