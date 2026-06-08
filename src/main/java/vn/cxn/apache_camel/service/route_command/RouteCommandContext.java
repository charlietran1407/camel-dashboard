package vn.cxn.apache_camel.service.route_command;

import java.util.Map;

public class RouteCommandContext {

    private final String targetId;
    private final Map<Object, Object> eventMap;

    public RouteCommandContext(String targetId, Map<Object, Object> eventMap) {
        this.targetId = targetId;
        this.eventMap = eventMap;
    }

    public String targetId() {
        return targetId;
    }

    public Map<Object, Object> eventMap() {
        return eventMap;
    }
}
