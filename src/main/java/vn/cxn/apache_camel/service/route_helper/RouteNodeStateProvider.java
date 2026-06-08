package vn.cxn.apache_camel.service.route_helper;

import java.util.List;
import java.util.Map;
import vn.cxn.apache_camel.model.entity.ClusterNodeEntity;
import vn.cxn.apache_camel.model.entity.RouteRuntimeStateEntity;

public interface RouteNodeStateProvider {
    List<Map<String, Object>> getNodeStatesForRoute(
            String routeId,
            List<ClusterNodeEntity> onlineNodes,
            Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode);
}
