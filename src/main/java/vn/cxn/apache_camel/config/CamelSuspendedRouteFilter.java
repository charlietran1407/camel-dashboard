package vn.cxn.apache_camel.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CamelSuspendedRouteFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CamelSuspendedRouteFilter.class);

    private final CamelContext camelContext;

    public CamelSuspendedRouteFilter(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Value("${camel.rest.context-path:/cameldash}")
    private String restContextPath;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (request instanceof HttpServletRequest httpRequest
                && response instanceof HttpServletResponse httpResponse) {
            String uri = httpRequest.getRequestURI();
            String contextPath = httpRequest.getContextPath();
            String path = uri.substring(contextPath.length());

            // Normalize context path for checking prefix
            String normalizedContextPath = restContextPath;
            if (!normalizedContextPath.endsWith("/")) {
                normalizedContextPath += "/";
            }
            if (!normalizedContextPath.startsWith("/")) {
                normalizedContextPath = "/" + normalizedContextPath;
            }

            // Check if path starts with dynamic context path
            if (path.startsWith(normalizedContextPath)) {
                try {
                    for (Route route : camelContext.getRoutes()) {
                        String endpointUri = route.getEndpoint().getEndpointUri();

                        String routePath = null;
                        if (endpointUri != null) {
                            if (endpointUri.startsWith("platform-http:")) {
                                routePath = extractPlatformHttpPath(endpointUri);
                            } else if (endpointUri.startsWith("rest:")) {
                                routePath = extractRestPath(endpointUri);
                            }
                        }

                        if (routePath != null) {
                            String cleanContextPath = restContextPath;
                            if (cleanContextPath.endsWith("/")) {
                                cleanContextPath =
                                        cleanContextPath.substring(
                                                0, cleanContextPath.length() - 1);
                            }
                            if (!cleanContextPath.startsWith("/")) {
                                cleanContextPath = "/" + cleanContextPath;
                            }
                            String fullRoutePath = cleanContextPath + routePath;

                            if (matchPath(fullRoutePath, path)) {
                                ServiceStatus status =
                                        camelContext
                                                .getRouteController()
                                                .getRouteStatus(route.getId());
                                if (status != null && !status.isStarted()) {
                                    log.warn(
                                            "Intercepted request to inactive route: {} (Status: {},"
                                                    + " Route ID: {}, Endpoint: {})",
                                            path,
                                            status,
                                            route.getId(),
                                            endpointUri);

                                    int statusCode = status == ServiceStatus.Suspended ? 503 : 404;
                                    httpResponse.setStatus(statusCode);
                                    httpResponse.setContentType("application/json;charset=UTF-8");

                                    String errorType =
                                            status == ServiceStatus.Suspended
                                                    ? "Service Unavailable"
                                                    : "Not Found";
                                    String msg =
                                            status == ServiceStatus.Suspended
                                                    ? "The requested service is temporarily"
                                                            + " suspended."
                                                    : "The requested route is not active.";

                                    httpResponse
                                            .getWriter()
                                            .write(
                                                    String.format(
                                                            "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                                                            statusCode, errorType, msg));
                                    return; // Stop the request synchronously here, preventing the
                                    // platform-http async
                                    // bug!
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error inspecting routes in CamelSuspendedRouteFilter", e);
                }
            }
        }

        chain.doFilter(request, response);
    }

    private String extractPlatformHttpPath(String endpointUri) {
        String path = endpointUri.substring("platform-http:".length());
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }
        path = path.replace('\\', '/').replaceAll("/+", "/");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private String extractRestPath(String endpointUri) {
        // e.g. "rest://post:upload-file" or "rest:get:/api/users"
        String path = endpointUri.substring("rest:".length());

        if (path.startsWith("//")) {
            path = path.substring(2);
        }

        int firstColon = path.indexOf(':');
        if (firstColon >= 0) {
            path = path.substring(firstColon + 1);
        }

        int secondColon = path.indexOf(':');
        if (secondColon >= 0) {
            String basePath = path.substring(0, secondColon);
            String uriTemplate = path.substring(secondColon + 1);

            int queryIdx = uriTemplate.indexOf('?');
            if (queryIdx >= 0) {
                uriTemplate = uriTemplate.substring(0, queryIdx);
            }
            path = basePath + "/" + uriTemplate;
        } else {
            int queryIdx = path.indexOf('?');
            if (queryIdx >= 0) {
                path = path.substring(0, queryIdx);
            }
        }

        path = path.replace('\\', '/').replaceAll("/+", "/");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private boolean matchPath(String routePath, String requestPath) {
        String r =
                routePath
                        .replaceAll("/+$", "")
                        .replaceAll("\\{[^/]+\\}", "*")
                        .replaceAll(":[^/]+", "*");
        String req = requestPath.replaceAll("/+$", "");

        if (r.contains("*")) {
            String regex = "^" + r.replace("*", "[^/]+") + "$";
            return req.matches(regex);
        }

        return r.equalsIgnoreCase(req);
    }
}
