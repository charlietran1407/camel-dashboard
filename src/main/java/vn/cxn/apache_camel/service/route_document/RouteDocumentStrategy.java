package vn.cxn.apache_camel.service.route_document;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RouteDocumentStrategy {

    boolean supports(String fileName);

    List<String> extractRouteIds(String content);

    Map<String, String> extractRouteDescriptions(String content, String serviceId);

    Set<String> extractEndpointSignatures(String content);

    String extractApiContextPathSignature(String content);

    String rewriteRouteIds(
            String content, Map<String, String> routeIdMapping, String internalEndpointPrefix);

    String ensureRouteIds(String content);
}
