package vn.cxn.apache_camel.service.route_helper;

import java.util.List;
import java.util.Map;
import vn.cxn.apache_camel.model.dto.RestParamInfo;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.entity.RouteEntity;

public interface RestParamExtractor {
    List<RestParamInfo> extractRestParams(String id, String originalId, String sourceUri);

    List<RestParamInfo> extractFallbackRestParams(String id, String originalId, String sourceUri);

    /**
     * Fast overload: resolves the active version ID from pre-fetched maps instead of issuing
     * individual {@code routeRepository.findById()} or {@code
     * versionService.getVersionsByRouteId()} calls per route. Use this inside bulk operations like
     * {@code listRoutes()}.
     *
     * <p>{@code yamlContentCache} is a per-request mutable map; entries are populated on first
     * access so each unique version file is read from disk at most once.
     */
    List<RestParamInfo> extractFallbackRestParams(
            String id,
            String originalId,
            String sourceUri,
            Map<String, RouteEntity> routesMap,
            Map<String, RouteVersion> activeVersionsByServiceId,
            Map<String, List<org.apache.camel.model.rest.RestDefinition>> restDefinitionsCache);

    boolean isRestSourceUriMatching(String sourceUri, String verbMethod, String combinedPath);

    String normalizeRestPathForComparison(String path);

    ParsedRestUri parseRestUri(String uri);

    class ParsedRestUri {
        public String method;
        public String path;
        public String consumes;
        public String produces;
    }
}
