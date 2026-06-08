package vn.cxn.apache_camel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.springframework.stereotype.Component;

@Component
public class GlobalCamelErrorHandler extends RouteConfigurationBuilder {

    private final ObjectMapper objectMapper;

    public GlobalCamelErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void configuration() {
        // Apply for ALL routes in CamelContext
        routeConfiguration()
                .onException(Exception.class)
                .handled(
                        exchange -> {
                            org.apache.camel.Route route = exchange.getUnitOfWork().getRoute();
                            String endpointUri =
                                    route != null && route.getEndpoint() != null
                                            ? route.getEndpoint().getEndpointUri()
                                            : "";

                            if (endpointUri.startsWith("rest:")
                                    || endpointUri.startsWith("platform-http:")
                                    || endpointUri.startsWith("servlet:")) {
                                return true;
                            }
                            return false;
                        })
                .process(this::injectTraceAndFormatError)
                .log(
                        org.apache.camel.LoggingLevel.ERROR,
                        "Global Camel Error: ${exception.message} | TraceId: ${header.X-Trace-Id}")
                .log(org.apache.camel.LoggingLevel.ERROR, "${messageHistory}");
    }

    private void injectTraceAndFormatError(Exchange exchange) {
        // 1️⃣ Get traceId from OTel Context
        String traceId = "";
        try {
            Span span = Span.current();
            if (span.getSpanContext().isValid()) {
                traceId = span.getSpanContext().getTraceId();
            }
        } catch (Exception ignored) {
            // Fallback if OTel not start up yet or context is losed
        }

        exchange.getMessage().setHeader("X-Trace-Id", traceId);

        // 2️⃣ Get real exception
        Throwable ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        String errorMsg = ex != null ? ex.getMessage() : "Internal Server Error";

        // 3️⃣ Build standard JSON response
        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("traceId", traceId);
        errorResponse.put("error", errorMsg);
        errorResponse.put("timestamp", Instant.now().toString());

        // 4️⃣ Convert to JSON String manually to avoid type conversion issues later
        try {
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            exchange.getMessage().setBody(jsonResponse);
        } catch (Exception e) {
            exchange.getMessage().setBody("{\"success\":false,\"error\":\"" + errorMsg + "\"}");
        }

        // If route expose HTTP, set status & content-type
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
    }
}
