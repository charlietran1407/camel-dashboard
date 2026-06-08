package vn.cxn.apache_camel.service;

import java.util.Collection;

public interface RouteLifecycleService {
    void startRoute(String routeId) throws Exception;

    void stopRoute(String routeId) throws Exception;

    void suspendRoute(String routeId) throws Exception;

    void resumeRoute(String routeId) throws Exception;

    boolean removeRoute(String routeId) throws Exception;

    String deployFromVersion(String versionId) throws Exception;

    void cleanUpRestDefinitions(String serviceId, Collection<String> routeIds);

    void cleanUpRestDefinitions(String routeId);
}
