package vn.cxn.apache_camel.service.route_document;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import vn.cxn.apache_camel.util.CamelYamlUtils;

class RouteDescriptionExtractor {

    public Map<String, String> extractRouteDescriptions(String content, String serviceId) {
        Map<String, String> descriptions = new HashMap<>();
        YamlDocumentLoader.loadYamlDocuments(content)
                .forEach(doc -> collectYamlDescriptions(doc, serviceId, descriptions, null));
        return descriptions;
    }

    @SuppressWarnings("unchecked")
    private void collectYamlDescriptions(
            Object node, String serviceId, Map<String, String> descriptions, String parentKey) {
        if (node instanceof Map<?, ?> map) {
            Object routeObj = map.get("route");
            if (routeObj instanceof Map<?, ?> routeMap) {
                collectYamlRouteDescription(
                        (Map<Object, Object>) routeMap, serviceId, descriptions);
            }
            Object fromObj = map.get("from");
            if (fromObj instanceof Map<?, ?> fromMap) {
                collectYamlRouteDescription((Map<Object, Object>) fromMap, serviceId, descriptions);
            }

            if (parentKey != null
                    && CamelYamlUtils.HTTP_VERBS.contains(parentKey.toLowerCase(Locale.ROOT))) {
                Object idVal = map.get("routeId");
                if (idVal == null) {
                    idVal = map.get("id");
                }
                if (idVal != null) {
                    Object desc = map.get("description");
                    if (desc != null) {
                        descriptions.put(
                                CamelYamlUtils.toManagedRouteId(serviceId, idVal.toString()),
                                desc.toString());
                    }
                }
            }

            map.forEach(
                    (k, val) ->
                            collectYamlDescriptions(
                                    val,
                                    serviceId,
                                    descriptions,
                                    k instanceof String ? (String) k : null));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collectYamlDescriptions(item, serviceId, descriptions, parentKey));
        }
    }

    private void collectYamlRouteDescription(
            Map<Object, Object> routeMap, String serviceId, Map<String, String> descriptions) {
        Object id = routeMap.get("id");
        if (id != null) {
            Object desc = routeMap.get("description");
            if (desc != null) {
                descriptions.put(
                        CamelYamlUtils.toManagedRouteId(serviceId, id.toString()), desc.toString());
            }
        }
    }
}
