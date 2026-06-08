package vn.cxn.apache_camel.service.route_helper;

import java.util.List;
import vn.cxn.apache_camel.model.dto.RestParamInfo;

public interface RestParamExtractor {
    List<RestParamInfo> extractRestParams(String id, String originalId, String sourceUri);

    List<RestParamInfo> extractFallbackRestParams(String id, String originalId, String sourceUri);

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
