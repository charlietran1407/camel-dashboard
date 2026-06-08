package vn.cxn.apache_camel.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfiguration {

    @Bean
    public Tracer tracer(
            @Value("${spring.application.name}") String serviceName, OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName);
    }

    @Bean
    public ContextPropagators contextPropagators(OpenTelemetry openTelemetry) {
        return openTelemetry.getPropagators();
    }
}
