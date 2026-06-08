package vn.cxn.apache_camel.service.route_helper;

import vn.cxn.apache_camel.model.dto.RouteMetadata;

public interface RouteMetadataExtractor {
    RouteMetadata extractRouteMetadata(String id, String defaultDescription);
}
