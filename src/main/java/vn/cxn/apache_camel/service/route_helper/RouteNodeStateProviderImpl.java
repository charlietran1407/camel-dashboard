package vn.cxn.apache_camel.service.route_helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.entity.ClusterNodeEntity;
import vn.cxn.apache_camel.model.entity.RouteRuntimeStateEntity;
import vn.cxn.apache_camel.model.enums.RouteState;

@Service
public class RouteNodeStateProviderImpl implements RouteNodeStateProvider {

    @Override
    public List<Map<String, Object>> getNodeStatesForRoute(
            String routeId,
            List<ClusterNodeEntity> onlineNodes,
            Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode) {
        List<Map<String, Object>> nodeStates = new ArrayList<>();
        if (routeId == null || routeId.isBlank()) {
            return nodeStates;
        }
        Map<String, RouteRuntimeStateEntity> nodeStatesForRoute =
                statesByRouteAndNode.getOrDefault(routeId, Collections.emptyMap());
        for (ClusterNodeEntity node : onlineNodes) {
            String nodeInstanceId = node.getInstanceId();
            RouteRuntimeStateEntity stateEntity = nodeStatesForRoute.get(nodeInstanceId);

            Map<String, Object> nodeStateMap = new LinkedHashMap<>();
            nodeStateMap.put("instanceId", nodeInstanceId);
            nodeStateMap.put(
                    "status",
                    stateEntity != null
                            ? stateEntity.getCurrentState()
                            : RouteState.STOPPED.getValue());
            nodeStateMap.put(
                    "errorMessage", stateEntity != null ? stateEntity.getErrorMessage() : null);
            nodeStates.add(nodeStateMap);
        }
        return nodeStates;
    }
}
