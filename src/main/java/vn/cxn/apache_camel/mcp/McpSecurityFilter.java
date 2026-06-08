package vn.cxn.apache_camel.mcp;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpSecurityFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(McpSecurityFilter.class);

    private final String apiKey;

    public McpSecurityFilter(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            String generatedKey = UUID.randomUUID().toString();
            log.warn("***************************************************************");
            log.warn("[SECURITY] No MCP API key configured via 'camel.dashboard.mcp.api-key'.");
            log.warn("[SECURITY] A dynamic security key has been generated for /mcp:");
            log.warn("[SECURITY] KEY: {}", generatedKey);
            log.warn("***************************************************************");
            this.apiKey = generatedKey;
        } else {
            log.info("[SECURITY] MCP API key configured and loaded successfully.");
            this.apiKey = apiKey.trim();
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        if (path.endsWith("/mcp")) {
            String authHeader = httpRequest.getHeader("Authorization");
            String customHeader = httpRequest.getHeader("X-MCP-API-KEY");

            String providedKey = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                providedKey = authHeader.substring(7).trim();
            } else if (customHeader != null) {
                providedKey = customHeader.trim();
            }

            if (providedKey == null || !apiKey.equals(providedKey)) {
                log.warn(
                        "[SECURITY] Unauthorized access attempt to /mcp from IP: {}",
                        httpRequest.getRemoteAddr());
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                httpResponse
                        .getWriter()
                        .write("{\"error\": \"Unauthorized: Invalid or missing MCP API Key.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
