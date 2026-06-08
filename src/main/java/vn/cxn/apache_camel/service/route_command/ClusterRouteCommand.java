package vn.cxn.apache_camel.service.route_command;

import vn.cxn.apache_camel.model.enums.ClusterEventType;

public interface ClusterRouteCommand {

    ClusterEventType eventType();

    void execute(RouteCommandContext context) throws Exception;
}
