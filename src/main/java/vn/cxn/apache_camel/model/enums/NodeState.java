package vn.cxn.apache_camel.model.enums;

public enum NodeState {
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE");

    private final String value;

    NodeState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** Parses node state from String ignoring case. Returns OFFLINE if no match. */
    public static NodeState fromValue(String state) {
        if (state == null) {
            return OFFLINE;
        }
        for (var ns : values()) {
            if (ns.value.equalsIgnoreCase(state) || ns.name().equalsIgnoreCase(state)) {
                return ns;
            }
        }
        return OFFLINE;
    }
}
