package vn.cxn.apache_camel.service.route_document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import vn.cxn.apache_camel.util.CamelYamlUtils;

class RouteIdManager {

    private static final Logger log = LoggerFactory.getLogger(RouteIdManager.class);

    public String ensureRouteIds(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        try {
            List<Object> docs = YamlDocumentLoader.loadYamlDocuments(content);
            if (docs.isEmpty()) {
                return content;
            }
            boolean modified = false;
            for (Object doc : docs) {
                if (ensureIdsInNode(doc, false, null)) {
                    modified = true;
                }
            }
            if (!modified) {
                return content;
            }
            Yaml yaml = new Yaml();
            if (docs.size() == 1) {
                return yaml.dump(docs.get(0));
            }
            return yaml.dumpAll(docs.iterator());
        } catch (Exception e) {
            log.warn("Failed to auto-generate and insert route IDs: {}", e.getMessage());
            return content;
        }
    }

    private boolean ensureIdsInNode(Object node, boolean inRoute, String parentKey) {
        boolean modified = false;
        if (node instanceof Map<?, ?> map) {
            modified |= handleRestVerbDefinitions(map, parentKey);
            modified |= handleRestConfiguration(map, parentKey);
            modified |= handleOpenApiDefinition(map, parentKey);
            modified |= handleTemplatedRouteDefinition(map, parentKey);
            modified |= ensureRouteHasId(map);
            modified |= wrapFromInRouteIfNeeded(map, inRoute);
            modified |= processChildNodes(map, inRoute);
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                if (ensureIdsInNode(item, inRoute, parentKey)) {
                    modified = true;
                }
            }
        }
        return modified;
    }

    @SuppressWarnings("unchecked")
    private boolean handleRestVerbDefinitions(Map<?, ?> map, String parentKey) {
        Map<Object, Object> mutableMap = (Map<Object, Object>) map;
        if (parentKey != null
                && CamelYamlUtils.HTTP_VERBS.contains(parentKey.toLowerCase(Locale.ROOT))) {
            if (CamelYamlUtils.isMissingOrBlank(mutableMap, "routeId")
                    && CamelYamlUtils.isMissingOrBlank(mutableMap, "id")) {
                if (!CamelYamlUtils.delegatesToInternal(mutableMap)) {
                    mutableMap.put("routeId", CamelYamlUtils.getRandomId("route_"));
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean handleRestConfiguration(Map<?, ?> map, String parentKey) {
        Map<Object, Object> mutableMap = (Map<Object, Object>) map;
        if (parentKey != null && "restConfiguration".equalsIgnoreCase(parentKey)) {
            if (mutableMap.containsKey("apiContextPath")
                    && mutableMap.get("apiContextPath") != null
                    && !String.valueOf(mutableMap.get("apiContextPath")).isBlank()) {
                if (CamelYamlUtils.isMissingOrBlank(mutableMap, "apiContextRouteId")) {
                    mutableMap.put("apiContextRouteId", CamelYamlUtils.getRandomId("route_"));
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean handleOpenApiDefinition(Map<?, ?> map, String parentKey) {
        Map<Object, Object> mutableMap = (Map<Object, Object>) map;
        if (parentKey != null && "openApi".equalsIgnoreCase(parentKey)) {
            if (CamelYamlUtils.isMissingOrBlank(mutableMap, "routeId")) {
                mutableMap.put("routeId", CamelYamlUtils.getRandomId("route_"));
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean handleTemplatedRouteDefinition(Map<?, ?> map, String parentKey) {
        Map<Object, Object> mutableMap = (Map<Object, Object>) map;
        if (parentKey != null && "templatedRoute".equalsIgnoreCase(parentKey)) {
            if (CamelYamlUtils.isMissingOrBlank(mutableMap, "routeId")) {
                mutableMap.put("routeId", CamelYamlUtils.getRandomId("route_"));
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean ensureRouteHasId(Map<?, ?> map) {
        Map<Object, Object> mutableMap = (Map<Object, Object>) map;
        Object routeObj = mutableMap.get("route");
        if (routeObj instanceof Map<?, ?> routeMap) {
            Map<Object, Object> mutableRoute = (Map<Object, Object>) routeMap;
            if (CamelYamlUtils.isMissingOrBlank(mutableRoute, "id")) {
                mutableRoute.put("id", CamelYamlUtils.getRandomId("route_"));
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean wrapFromInRouteIfNeeded(Map<?, ?> map, boolean inRoute) {
        Map<Object, Object> mutableMap = (Map<Object, Object>) map;
        Object fromObj = mutableMap.get("from");
        if (fromObj instanceof Map<?, ?> fromMap) {
            boolean isRoute = mutableMap.get("route") instanceof Map<?, ?>;
            if (!inRoute && !isRoute) {
                mutableMap.remove("from");

                Map<Object, Object> routeMap = new java.util.LinkedHashMap<>();
                routeMap.put("id", CamelYamlUtils.getRandomId("route_"));
                routeMap.put("from", fromMap);

                mutableMap.put("route", routeMap);
                return true;
            }
        }
        return false;
    }

    private boolean processChildNodes(Map<?, ?> map, boolean inRoute) {
        boolean modified = false;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object val = entry.getValue();
            boolean nextInRoute = inRoute || "route".equals(key);
            String childParentKey = key instanceof String ? (String) key : null;
            if (ensureIdsInNode(val, nextInRoute, childParentKey)) {
                modified = true;
            }
        }
        return modified;
    }

    public List<String> extractRouteIds(String content) {
        List<String> ids = new ArrayList<>();
        for (Object doc : YamlDocumentLoader.loadYamlDocuments(content)) {
            collectRouteAndVerbIds(doc, ids, null);
        }
        return ids;
    }

    private void collectRouteAndVerbIds(Object node, List<String> ids, String parentKey) {
        if (node instanceof Map<?, ?> map) {
            Object routeObj = map.get("route");
            if (routeObj instanceof Map<?, ?> routeMap) {
                Object id = routeMap.get("id");
                if (id != null) {
                    ids.add(id.toString());
                }
            }
            Object fromObj = map.get("from");
            if (fromObj instanceof Map<?, ?> fromMap) {
                Object id = fromMap.get("id");
                if (id != null) {
                    ids.add(id.toString());
                }
            }
            Object routeId = map.get("routeId");
            if (routeId != null) {
                ids.add(routeId.toString());
            }
            Object apiContextRouteId = map.get("apiContextRouteId");
            if (apiContextRouteId != null) {
                ids.add(apiContextRouteId.toString());
            }

            if (parentKey != null
                    && CamelYamlUtils.HTTP_VERBS.contains(parentKey.toLowerCase(Locale.ROOT))) {
                if (routeId == null) {
                    Object id = map.get("id");
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
            }

            map.forEach(
                    (k, val) ->
                            collectRouteAndVerbIds(
                                    val, ids, k instanceof String ? (String) k : null));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collectRouteAndVerbIds(item, ids, parentKey));
        }
    }

    public String rewriteRouteIds(
            String content, Map<String, String> routeIdMapping, String internalEndpointPrefix) {
        List<Object> docs = YamlDocumentLoader.loadYamlDocuments(content);
        if (docs.isEmpty()) {
            return content;
        }
        docs.forEach(doc -> rewriteYamlNode(doc, routeIdMapping, internalEndpointPrefix));
        Yaml yaml = new Yaml();
        if (docs.size() == 1) {
            return yaml.dump(docs.get(0));
        }
        return yaml.dumpAll(docs.iterator());
    }

    @SuppressWarnings("unchecked")
    private void rewriteYamlNode(Object node, Map<String, String> routeIdMapping, String prefix) {
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> mutableMap = (Map<Object, Object>) map;

            // Handle split-form internal endpoints: uri: direct/seda/vm + parameters.name
            if (mutableMap.containsKey("uri") && mutableMap.containsKey("parameters")) {
                Object uriVal = mutableMap.get("uri");
                Object paramsVal = mutableMap.get("parameters");
                if (uriVal instanceof String uriStr && paramsVal instanceof Map<?, ?> paramsMap) {
                    String cleanUri = uriStr.trim();
                    if ("direct".equals(cleanUri)
                            || "seda".equals(cleanUri)
                            || "vm".equals(cleanUri)) {
                        Map<Object, Object> mutableParams = (Map<Object, Object>) paramsMap;
                        if (mutableParams.containsKey("name")) {
                            Object nameVal = mutableParams.get("name");
                            if (nameVal instanceof String nameStr && !nameStr.startsWith(prefix)) {
                                mutableParams.put("name", prefix + nameStr);
                            }
                        }
                    }
                }
            }

            rewriteProperty(mutableMap, "id", routeIdMapping);
            rewriteProperty(mutableMap, "routeId", routeIdMapping);
            rewriteProperty(mutableMap, "apiContextRouteId", routeIdMapping);

            mutableMap.forEach(
                    (key, val) -> {
                        if (val instanceof String strVal
                                && CamelYamlUtils.isInternalEndpoint(strVal)) {
                            String keyStr = String.valueOf(key);
                            if ("uri".equals(keyStr)
                                    || "to".equals(keyStr)
                                    || "toD".equals(keyStr)
                                    || "deadLetterUri".equals(keyStr)
                                    || "compensation".equals(keyStr)
                                    || "completion".equals(keyStr)) {
                                mutableMap.put(
                                        key, CamelYamlUtils.rewriteInternalUri(strVal, prefix));
                            }
                        }
                    });

            map.values().forEach(value -> rewriteYamlNode(value, routeIdMapping, prefix));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> rewriteYamlNode(item, routeIdMapping, prefix));
        }
    }

    private void rewriteProperty(Map<Object, Object> map, String key, Map<String, String> mapping) {
        Object val = map.get(key);
        if (val != null && mapping.containsKey(val.toString())) {
            map.put(key, mapping.get(val.toString()));
        }
    }
}
