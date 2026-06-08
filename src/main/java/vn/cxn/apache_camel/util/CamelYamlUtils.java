package vn.cxn.apache_camel.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CamelYamlUtils {

    public static final Set<String> HTTP_VERBS =
            Set.of("get", "post", "put", "delete", "patch", "head", "options", "trace");

    public static final Set<String> INTERNAL_SCHEMES = Set.of("direct:", "seda:", "vm:");

    public static final Set<String> PUBLIC_SCHEMES =
            Set.of(
                    "platform-http:",
                    "servlet:",
                    "rest:",
                    "undertow:",
                    "jetty:",
                    "netty-http:",
                    "http:",
                    "https:",
                    "rest-openapi:");

    private CamelYamlUtils() {
        // Prevent instantiation
    }

    public static boolean isMissingOrBlank(Map<?, ?> map, String key) {
        return !map.containsKey(key)
                || map.get(key) == null
                || String.valueOf(map.get(key)).isBlank();
    }

    public static boolean delegatesToInternal(Map<?, ?> verbMap) {
        Object toObj = verbMap.get("to");
        if (toObj == null) {
            return false;
        }
        String toUri = null;
        if (toObj instanceof String str) {
            toUri = str;
        } else if (toObj instanceof Map<?, ?> toMap) {
            Object uriVal = toMap.get("uri");
            if (uriVal != null) {
                toUri = uriVal.toString();
            }
        }
        return isInternalEndpoint(toUri);
    }

    public static boolean isInternalEndpoint(String uri) {
        if (uri == null) {
            return false;
        }
        String trimUri = uri.trim();
        return INTERNAL_SCHEMES.stream().anyMatch(trimUri::startsWith);
    }

    public static boolean isPublicEndpoint(String normalizedUri) {
        return PUBLIC_SCHEMES.stream().anyMatch(normalizedUri::startsWith);
    }

    public static String normalizeEndpoint(String uri) {
        if (uri == null) {
            return "";
        }
        String normalized = uri.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        return normalized.replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    public static String normalizeRestPath(String contextPath, String basePath, String childPath) {
        String ctx = valueAsString(contextPath);
        String base = valueAsString(basePath);
        String child = valueAsString(childPath);
        String combined =
                ("/" + ctx + "/" + base + "/" + child).replace('\\', '/').replaceAll("/+", "/");
        return combined.replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    public static String valueAsString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public static String getRandomId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String toManagedRouteId(String serviceId, String originalRouteId) {
        if (serviceId == null
                || serviceId.isBlank()
                || originalRouteId == null
                || originalRouteId.isBlank()) {
            return originalRouteId;
        }
        String prefix = "svc_" + serviceId.replaceAll("[^A-Za-z0-9_-]", "_") + "__";
        if (originalRouteId.startsWith(prefix)) {
            return originalRouteId;
        }
        return prefix + originalRouteId;
    }

    public static String rewriteInternalUri(String uri, String prefix) {
        if (uri == null || prefix == null || prefix.isEmpty()) {
            return uri;
        }
        String trimmed = uri.trim();
        String scheme =
                INTERNAL_SCHEMES.stream().filter(trimmed::startsWith).findFirst().orElse(null);
        if (scheme == null) {
            return uri;
        }

        String path = trimmed.substring(scheme.length());
        String slashes = "";
        if (path.startsWith("//")) {
            slashes = "//";
            path = path.substring(2);
        }

        if (path.startsWith(prefix)) {
            return uri;
        }

        int qIndex = path.indexOf('?');
        String endpointName = qIndex >= 0 ? path.substring(0, qIndex) : path;
        String query = qIndex >= 0 ? path.substring(qIndex) : "";

        return scheme + slashes + prefix + endpointName + query;
    }
}
