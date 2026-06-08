package vn.cxn.apache_camel.service.route_helper;

import vn.cxn.apache_camel.model.dto.RouteVersion;

public interface AuditLogger {
    void logRouteAction(String routeId, String action);

    void logDeploymentSuccess(RouteVersion rv, String primaryId);

    void logDeploymentFailure(RouteVersion rv, String errorMsg);
}
