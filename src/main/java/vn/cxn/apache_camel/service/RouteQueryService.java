package vn.cxn.apache_camel.service;

import java.util.List;
import java.util.Map;
import vn.cxn.apache_camel.model.dto.RouteInfo;

public interface RouteQueryService {
    List<RouteInfo> listRoutes();

    String getRouteStatus(String routeId);

    List<Map<String, Object>> getServicesWithDetails();
}
