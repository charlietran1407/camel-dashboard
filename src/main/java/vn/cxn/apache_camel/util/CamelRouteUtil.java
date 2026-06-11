package vn.cxn.apache_camel.util;

import java.util.Collection;
import java.util.Objects;
import org.apache.camel.model.rest.VerbDefinition;

public final class CamelRouteUtil {

    /**
     * Prefix used to namespace managed route IDs by their service. Full pattern:
     * svc_&lt;serviceId&gt;__
     */
    public static final String SERVICE_ROUTE_PREFIX = "svc_";

    private CamelRouteUtil() {
        // Prevent instantiation
    }

    /**
     * Normalizes a path by replacing backslashes, resolving multiple slashes, and removing trailing
     * slashes.
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return "/";
        }
        var cleaned = path.replace('\\', '/').replaceAll("/+", "/").replaceAll("/+$", "");
        return cleaned.isEmpty() ? "/" : cleaned;
    }

    /**
     * Checks if a given path matches any of the paths in the collection after normalization and
     * case-insensitive comparison.
     */
    public static boolean pathMatchesAny(String path, Collection<String> paths) {
        if (path == null || paths == null || paths.isEmpty()) {
            return false;
        }
        var normalized = normalizePath(path);
        return paths.stream()
                .filter(Objects::nonNull)
                .anyMatch(p -> normalizePath(p).equalsIgnoreCase(normalized));
    }

    /** Generates a standardized service prefix for matching route IDs. */
    public static String getServicePrefix(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return null;
        }
        return SERVICE_ROUTE_PREFIX + serviceId.replaceAll("[^A-Za-z0-9_-]", "_") + "__";
    }

    /**
     * Checks if a route ID belongs to a service prefix or is contained in/ends with any of the
     * target route IDs.
     */
    public static boolean routeBelongsToService(
            String routeId, String servicePrefix, Collection<String> allRouteIds) {
        if (routeId == null) {
            return false;
        }
        if (servicePrefix != null && routeId.startsWith(servicePrefix)) {
            return true;
        }
        if (allRouteIds != null) {
            if (allRouteIds.contains(routeId)) {
                return true;
            }
            return allRouteIds.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(rId -> rId.endsWith("__" + routeId) || routeId.endsWith("__" + rId));
        }
        return false;
    }

    /**
     * Checks if a VerbDefinition belongs to a service or route IDs, using both direct ID matching
     * and fallback toUri matching.
     */
    public static boolean verbBelongsToService(
            VerbDefinition verb,
            String serviceId,
            String servicePrefix,
            Collection<String> allRouteIds) {
        if (verb == null) {
            return false;
        }
        var verbRouteId = verb.getRouteId();
        if (routeBelongsToService(verbRouteId, servicePrefix, allRouteIds)) {
            return true;
        }

        // Fallback check on toUri
        if (verb.getTo() != null && verb.getTo().getUri() != null) {
            var toUri = verb.getTo().getUri();
            if (serviceId != null && toUri.contains(getServicePrefix(serviceId))) {
                return true;
            }
            if (allRouteIds != null) {
                return allRouteIds.stream().filter(Objects::nonNull).anyMatch(toUri::contains);
            }
        }
        return false;
    }

    /**
     * Isolates and extracts the path portion from a Camel REST endpoint URI (e.g.
     * rest://get:/api/logs?param=1 -> /api/logs).
     */
    public static String extractPathFromRestUri(String uri) {
        if (uri == null) {
            return "";
        }
        var cleanUri = uri.replaceAll("\\?.*$", "").trim();
        if (cleanUri.startsWith("rest-api://")) {
            return cleanUri.substring(11);
        } else if (cleanUri.startsWith("rest-api:")) {
            return cleanUri.substring(9);
        } else if (cleanUri.startsWith("rest-openapi://")) {
            return cleanUri.substring(15);
        } else if (cleanUri.startsWith("rest-openapi:")) {
            return cleanUri.substring(13);
        }
        String sub = null;
        if (cleanUri.startsWith("rest://")) {
            sub = cleanUri.substring(7);
        } else if (cleanUri.startsWith("rest:")) {
            sub = cleanUri.substring(5);
        }
        if (sub == null) {
            return "";
        }
        var colonIdx = sub.indexOf(':');
        return colonIdx >= 0 ? sub.substring(colonIdx + 1) : sub;
    }
}
