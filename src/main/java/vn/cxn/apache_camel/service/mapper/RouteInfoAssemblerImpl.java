package vn.cxn.apache_camel.service.mapper;

import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.dto.RouteInfo;

@Component
public class RouteInfoAssemblerImpl implements RouteInfoAssembler {

    @Override
    public RouteInfo assemble(RouteInfo routeInfo) {
        return routeInfo;
    }
}
