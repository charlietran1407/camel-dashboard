package vn.cxn.apache_camel.model.enums;

public enum ClusterEventType {
    START_ROUTE("START_ROUTE"),
    STOP_ROUTE("STOP_ROUTE"),
    SUSPEND_ROUTE("SUSPEND_ROUTE"),
    RESUME_ROUTE("RESUME_ROUTE"),
    REMOVE_ROUTE("REMOVE_ROUTE"),
    DEPLOY_VERSION("DEPLOY_VERSION"),
    DELETE_VERSION("DELETE_VERSION"),
    UNKNOWN("UNKNOWN");

    private final String value;

    ClusterEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ClusterEventType fromValue(String type) {
        if (type == null) {
            return UNKNOWN;
        }
        for (var cet : values()) {
            if (cet.value.equalsIgnoreCase(type) || cet.name().equalsIgnoreCase(type)) {
                return cet;
            }
        }
        return UNKNOWN;
    }
}
