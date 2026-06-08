package vn.cxn.apache_camel.service.route_helper;

import vn.cxn.apache_camel.model.dto.RouteVersion;

public interface RouteVersionContentHelper {
    String normalizeResourceName(String fileName);

    String readDeploymentContent(RouteVersion rv);
}
