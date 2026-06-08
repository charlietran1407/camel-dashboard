package vn.cxn.apache_camel.service;

import java.util.List;
import java.util.Map;
import vn.cxn.apache_camel.model.entity.RouteRuntimeStateEntity;

public interface ClusterNodeService {
    void init();

    String getInstanceId();

    List<Map<String, Object>> getAllNodes();

    boolean evictNode(String targetInstanceId);

    void updateLocalRouteStates();

    List<RouteRuntimeStateEntity> getAllRouteStates();

    boolean isRedisEnabled();
}
