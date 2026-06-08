package vn.cxn.apache_camel.service.route_document;

import java.util.List;
import java.util.Map;

class RestConfigProcessor {

    public String extractContextPath(List<Object> docs) {
        for (Object doc : docs) {
            String path = findRestConfigurationPath(doc, "contextPath");
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    public String extractApiContextPath(List<Object> docs) {
        for (Object doc : docs) {
            String path = findRestConfigurationPath(doc, "apiContextPath");
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private String findRestConfigurationPath(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            Object restConfigObj = map.get("restConfiguration");
            if (restConfigObj instanceof Map<?, ?> restConfigMap) {
                Object val = restConfigMap.get(key);
                if (val != null) {
                    return val.toString().trim();
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!"restConfiguration".equals(entry.getKey())) {
                    String found = findRestConfigurationPath(entry.getValue(), key);
                    if (found != null) {
                        return found;
                    }
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                String found = findRestConfigurationPath(item, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
