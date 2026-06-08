package vn.cxn.apache_camel.service.route_helper;

import vn.cxn.apache_camel.model.dto.RouteVersion;

public interface RouteRegistrationService {
    void registerRoutesInDb(RouteVersion version);
}
