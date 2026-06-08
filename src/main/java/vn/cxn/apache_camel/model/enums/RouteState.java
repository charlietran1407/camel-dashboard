package vn.cxn.apache_camel.model.enums;

public enum RouteState {
    STARTED("Started"),
    STOPPED("Stopped"),
    SUSPENDED("Suspended"),
    UNKNOWN("UNKNOWN");

    private final String value;

    RouteState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** Parses state from String ignoring case. Returns UNKNOWN if no match. */
    public static RouteState fromValue(String state) {
        if (state == null) {
            return UNKNOWN;
        }
        for (var rs : values()) {
            if (rs.value.equalsIgnoreCase(state) || rs.name().equalsIgnoreCase(state)) {
                return rs;
            }
        }
        return UNKNOWN;
    }
}
