package vn.cxn.apache_camel.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelDashboardMcpConfig {

    @Value("${camel.dashboard.mcp.api-key:}")
    private String apiKey;

    @Bean
    public ToolCallbackProvider camelDashboardToolCallbackProvider(
            CamelDashboardMcpTools camelDashboardMcpTools) {
        return MethodToolCallbackProvider.builder().toolObjects(camelDashboardMcpTools).build();
    }

    @Bean
    public FilterRegistrationBean<McpSecurityFilter> mcpSecurityFilter() {
        FilterRegistrationBean<McpSecurityFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new McpSecurityFilter(apiKey));
        registrationBean.addUrlPatterns("/mcp");
        registrationBean.setOrder(1); // Execute early in the filter chain
        return registrationBean;
    }
}
