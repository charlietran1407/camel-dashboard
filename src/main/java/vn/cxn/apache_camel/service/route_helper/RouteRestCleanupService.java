package vn.cxn.apache_camel.service.route_helper;

import java.util.Collection;

public interface RouteRestCleanupService {
    void cleanUpRestDefinitions(String serviceId, Collection<String> routeIds);

    void cleanUpRestDefinitions(String routeId);
}
