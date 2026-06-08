package vn.cxn.apache_camel.service.route_command;

import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.enums.ClusterEventType;
import vn.cxn.apache_camel.service.CamelRouteService;

@Component
public class RemoveRouteCommandImpl implements ClusterRouteCommand {

    private final CamelRouteService camelRouteService;

    public RemoveRouteCommandImpl(CamelRouteService camelRouteService) {
        this.camelRouteService = camelRouteService;
    }

    @Override
    public ClusterEventType eventType() {
        return ClusterEventType.REMOVE_ROUTE;
    }

    @Override
    public void execute(RouteCommandContext context) throws Exception {
        camelRouteService.removeRoute(context.targetId());
    }
}
