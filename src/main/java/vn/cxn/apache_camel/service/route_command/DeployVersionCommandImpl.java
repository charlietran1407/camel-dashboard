package vn.cxn.apache_camel.service.route_command;

import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.enums.ClusterEventType;
import vn.cxn.apache_camel.service.CamelRouteService;

@Component
public class DeployVersionCommandImpl implements ClusterRouteCommand {

    private final CamelRouteService camelRouteService;

    public DeployVersionCommandImpl(CamelRouteService camelRouteService) {
        this.camelRouteService = camelRouteService;
    }

    @Override
    public ClusterEventType eventType() {
        return ClusterEventType.DEPLOY_VERSION;
    }

    @Override
    public void execute(RouteCommandContext context) throws Exception {
        camelRouteService.deployFromVersion(context.targetId());
    }
}
