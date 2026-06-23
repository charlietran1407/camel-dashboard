package vn.cxn.apache_camel.service;

import java.util.List;
import java.util.Map;
import vn.cxn.apache_camel.model.dto.RouteRuntimeState;

public interface ClusterNodeService {
    void init();

    String getInstanceId();

    List<Map<String, Object>> getAllNodes();

    boolean evictNode(String targetInstanceId);

    void updateLocalRouteStates();

    List<RouteRuntimeState> getAllRouteStates();

    boolean isRedisEnabled();
}
