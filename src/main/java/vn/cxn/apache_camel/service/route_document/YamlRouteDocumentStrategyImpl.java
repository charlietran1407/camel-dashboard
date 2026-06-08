package vn.cxn.apache_camel.service.route_document;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class YamlRouteDocumentStrategyImpl implements RouteDocumentStrategy {

    private final RouteIdManager routeIdManager = new RouteIdManager();
    private final EndpointSignatureExtractor endpointSignatureExtractor =
            new EndpointSignatureExtractor();
    private final RouteDescriptionExtractor routeDescriptionExtractor =
            new RouteDescriptionExtractor();

    @Override
    public boolean supports(String fileName) {
        return fileName != null && (fileName.endsWith(".yaml") || fileName.endsWith(".yml"));
    }

    @Override
    public List<String> extractRouteIds(String content) {
        return routeIdManager.extractRouteIds(content);
    }

    @Override
    public Map<String, String> extractRouteDescriptions(String content, String serviceId) {
        return routeDescriptionExtractor.extractRouteDescriptions(content, serviceId);
    }

    @Override
    public Set<String> extractEndpointSignatures(String content) {
        return endpointSignatureExtractor.extractEndpointSignatures(content);
    }

    @Override
    public String extractApiContextPathSignature(String content) {
        try {
            List<Object> docs = YamlDocumentLoader.loadYamlDocuments(content);
            if (docs.isEmpty()) {
                return null;
            }
            RestConfigProcessor restConfigProcessor = new RestConfigProcessor();
            String contextPath = restConfigProcessor.extractContextPath(docs);
            String apiContextPath = restConfigProcessor.extractApiContextPath(docs);
            if (apiContextPath != null && !apiContextPath.isBlank()) {
                String normalized =
                        vn.cxn.apache_camel.util.CamelYamlUtils.normalizeRestPath(
                                contextPath, apiContextPath, "");
                if (!normalized.isBlank() && !"/".equals(normalized)) {
                    return "rest:" + normalized;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    @Override
    public String rewriteRouteIds(
            String content, Map<String, String> routeIdMapping, String internalEndpointPrefix) {
        return routeIdManager.rewriteRouteIds(content, routeIdMapping, internalEndpointPrefix);
    }

    @Override
    public String ensureRouteIds(String content) {
        return routeIdManager.ensureRouteIds(content);
    }
}
