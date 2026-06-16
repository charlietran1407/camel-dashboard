package vn.cxn.apache_camel.service.route_helper;

import vn.cxn.apache_camel.model.dto.RouteMetadata;

public interface RouteMetadataExtractor {
    RouteMetadata extractRouteMetadata(String id, String defaultDescription);

    RouteMetadata extractRouteMetadata(
            String id,
            String defaultDescription,
            java.util.Map<String, vn.cxn.apache_camel.model.entity.RouteEntity> routesMap,
            java.util.Map<String, vn.cxn.apache_camel.model.dto.RouteVersion>
                    activeVersionsByServiceId,
            java.util.Map<String, java.lang.Integer> totalVersionsByServiceId);
}
