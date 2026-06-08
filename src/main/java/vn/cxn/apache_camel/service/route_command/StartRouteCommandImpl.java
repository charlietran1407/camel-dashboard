package vn.cxn.apache_camel.service.route_command;

import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.enums.ClusterEventType;
import vn.cxn.apache_camel.service.CamelRouteService;

@Component
public class StartRouteCommandImpl implements ClusterRouteCommand {

    private final CamelRouteService camelRouteService;

    public StartRouteCommandImpl(CamelRouteService camelRouteService) {
        this.camelRouteService = camelRouteService;
    }

    @Override
    public ClusterEventType eventType() {
        return ClusterEventType.START_ROUTE;
    }

    @Override
    public void execute(RouteCommandContext context) throws Exception {
        camelRouteService.startRoute(context.targetId());
    }
}
