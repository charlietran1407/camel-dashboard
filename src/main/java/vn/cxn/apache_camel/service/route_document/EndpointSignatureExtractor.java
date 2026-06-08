package vn.cxn.apache_camel.service.route_document;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.cxn.apache_camel.util.CamelYamlUtils;

class EndpointSignatureExtractor {

    private static final Logger log = LoggerFactory.getLogger(EndpointSignatureExtractor.class);

    public Set<String> extractEndpointSignatures(String content) {
        Set<String> signatures = new LinkedHashSet<>();
        try {
            List<Object> docs = YamlDocumentLoader.loadYamlDocuments(content);
            if (docs.isEmpty()) {
                return signatures;
            }

            RestConfigProcessor restConfigProcessor = new RestConfigProcessor();
            String contextPath = restConfigProcessor.extractContextPath(docs);
            String apiContextPath = restConfigProcessor.extractApiContextPath(docs);

            if (apiContextPath != null && !apiContextPath.isBlank()) {
                addRestApiSignature(signatures, contextPath, apiContextPath);
            }

            for (Object doc : docs) {
                collectYamlEndpointSignatures(doc, signatures, contextPath);
            }
        } catch (Exception e) {
            log.warn("Failed to extract endpoint signatures: {}", e.getMessage());
        }
        return signatures;
    }

    @SuppressWarnings("unchecked")
    private void collectYamlEndpointSignatures(
            Object node, Set<String> signatures, String contextPath) {
        if (node instanceof Map<?, ?> map) {
            Object routeObj = map.get("route");
            if (routeObj instanceof Map<?, ?> routeMap) {
                collectYamlFromSignature((Map<Object, Object>) routeMap, signatures);
            }
            Object fromObj = map.get("from");
            if (fromObj instanceof Map<?, ?> fromMap) {
                addYamlUriSignature(fromMap.get("uri"), signatures);
            }
            Object restObj = map.get("rest");
            if (restObj instanceof Map<?, ?> restMap) {
                collectYamlRestSignature((Map<Object, Object>) restMap, signatures, contextPath);
            }
            map.values()
                    .forEach(
                            value -> collectYamlEndpointSignatures(value, signatures, contextPath));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collectYamlEndpointSignatures(item, signatures, contextPath));
        }
    }

    private void collectYamlFromSignature(Map<Object, Object> routeMap, Set<String> signatures) {
        Object fromObj = routeMap.get("from");
        if (fromObj instanceof Map<?, ?> fromMap) {
            addYamlUriSignature(fromMap.get("uri"), signatures);
        }
    }

    private void collectYamlRestSignature(
            Map<Object, Object> restMap, Set<String> signatures, String contextPath) {
        Object basePath = restMap.get("path");
        addRestSignature(signatures, contextPath, CamelYamlUtils.valueAsString(basePath), "");
        restMap.forEach(
                (key, value) -> {
                    if (value instanceof List<?> list) {
                        list.forEach(
                                item ->
                                        collectRestVerbPath(
                                                contextPath,
                                                CamelYamlUtils.valueAsString(basePath),
                                                item,
                                                signatures));
                    } else {
                        collectRestVerbPath(
                                contextPath,
                                CamelYamlUtils.valueAsString(basePath),
                                value,
                                signatures);
                    }
                });
    }

    private void collectRestVerbPath(
            String contextPath, String basePath, Object node, Set<String> signatures) {
        if (node instanceof Map<?, ?> map) {
            Object path = map.get("path");
            if (path != null) {
                addRestSignature(signatures, contextPath, basePath, path.toString());
            }
        }
    }

    private void addYamlUriSignature(Object uri, Set<String> signatures) {
        if (uri != null) {
            addEndpointSignature(signatures, uri.toString());
        }
    }

    private void addEndpointSignature(Set<String> signatures, String uri) {
        String normalized = CamelYamlUtils.normalizeEndpoint(uri);
        if (!normalized.isBlank() && CamelYamlUtils.isPublicEndpoint(normalized)) {
            signatures.add("endpoint:" + normalized);
        }
    }

    private void addRestSignature(
            Set<String> signatures, String contextPath, String basePath, String childPath) {
        String normalized = CamelYamlUtils.normalizeRestPath(contextPath, basePath, childPath);
        if (!normalized.isBlank() && !"/".equals(normalized)) {
            signatures.add("rest:" + normalized);
        }
    }

    private void addRestApiSignature(
            Set<String> signatures, String contextPath, String apiContextPath) {
        String normalized = CamelYamlUtils.normalizeRestPath(contextPath, apiContextPath, "");
        if (!normalized.isBlank() && !"/".equals(normalized)) {
            signatures.add("rest:" + normalized);
        }
    }
}
