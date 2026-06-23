package vn.cxn.apache_camel.service.component;

import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Parses a Camel YAML DSL route file using an in-memory mock {@link org.apache.camel.CamelContext}
 * to dynamically extract schemes, languages, data formats, and other dependencies.
 */
@Service
public class CamelYamlComponentScanner {

    private static final Logger log = LoggerFactory.getLogger(CamelYamlComponentScanner.class);

    private static final java.util.regex.Pattern MODELINE_PATTERN =
            java.util.regex.Pattern.compile(
                    "^#\\s*camel-dashboard\\s*:\\s*dependency\\s*=\\s*(.+)");

    /** Scan {@code yamlContent} and return a {@link ScanResult} with all detected identifiers. */
    public ScanResult scan(String yamlContent) {
        Set<String> schemes = new LinkedHashSet<>();
        Set<String> eipHints = new LinkedHashSet<>();
        Set<String> dataFormats = new LinkedHashSet<>();
        Set<String> languages = new LinkedHashSet<>();
        Set<String> extraDependencies = new LinkedHashSet<>();
        Set<String> explicitDependencies = new LinkedHashSet<>();

        if (yamlContent == null || yamlContent.isBlank()) {
            return new ScanResult(
                    schemes,
                    eipHints,
                    dataFormats,
                    languages,
                    extraDependencies,
                    explicitDependencies);
        }

        parseModelineDependencies(yamlContent, explicitDependencies);

        DefaultCamelContext context = new DefaultCamelContext();
        context.setAutowiredEnabled(false);
        context.setClassResolver(
                new ScanningClassResolver(context.getClassResolver(), schemes, dataFormats));
        try {
            // 1. Intercept component resolution to capture schemes
            context.getCamelContextExtension()
                    .addContextPlugin(
                            org.apache.camel.spi.ComponentResolver.class,
                            (name, camelContext) -> {
                                schemes.add(name);
                                return new org.apache.camel.support.DefaultComponent(camelContext) {
                                    @Override
                                    protected org.apache.camel.Endpoint createEndpoint(
                                            String uri,
                                            String remaining,
                                            java.util.Map<String, Object> parameters) {
                                        parameters.clear(); // Clear parameters to skip validation
                                        // failure
                                        return new org.apache.camel.support.DefaultEndpoint(
                                                uri, this) {
                                            @Override
                                            public org.apache.camel.Producer createProducer()
                                                    throws Exception {
                                                return new org.apache.camel.support.DefaultProducer(
                                                        this) {
                                                    @Override
                                                    public void process(
                                                            org.apache.camel.Exchange exchange)
                                                            throws Exception {}
                                                };
                                            }

                                            @Override
                                            public org.apache.camel.Consumer createConsumer(
                                                    org.apache.camel.Processor processor)
                                                    throws Exception {
                                                return new org.apache.camel.support.DefaultConsumer(
                                                        this, processor);
                                            }
                                        };
                                    }
                                };
                            });

            // 2. Intercept language resolution to capture expression languages
            context.getCamelContextExtension()
                    .addContextPlugin(
                            org.apache.camel.spi.LanguageResolver.class,
                            (name, camelContext) -> {
                                languages.add(name);
                                return new org.apache.camel.spi.Language() {
                                    @Override
                                    public org.apache.camel.Predicate createPredicate(
                                            String expression) {
                                        return new org.apache.camel.Predicate() {
                                            @Override
                                            public boolean matches(
                                                    org.apache.camel.Exchange exchange) {
                                                return true;
                                            }
                                        };
                                    }

                                    @Override
                                    public org.apache.camel.Expression createExpression(
                                            String expression) {
                                        return new org.apache.camel.Expression() {
                                            @Override
                                            @SuppressWarnings("unchecked")
                                            public <T> T evaluate(
                                                    org.apache.camel.Exchange exchange,
                                                    Class<T> type) {
                                                if (type == null || type == Object.class) {
                                                    return (T) new Object();
                                                }
                                                try {
                                                    return type.getDeclaredConstructor()
                                                            .newInstance();
                                                } catch (Exception ignored) {
                                                    try {
                                                        return (T) new Object();
                                                    } catch (Exception e) {
                                                        return null;
                                                    }
                                                }
                                            }
                                        };
                                    }
                                };
                            });

            // 3. Intercept dataformat resolution to capture data formats
            context.getCamelContextExtension()
                    .addContextPlugin(
                            org.apache.camel.spi.DataFormatResolver.class,
                            (name, camelContext) -> {
                                dataFormats.add(name);
                                class MockDataFormat
                                        implements org.apache.camel.spi.DataFormat,
                                                org.apache.camel.spi.PropertyConfigurerAware {
                                    @Override
                                    public void marshal(
                                            org.apache.camel.Exchange exchange,
                                            Object graph,
                                            java.io.OutputStream stream) {}

                                    @Override
                                    public Object unmarshal(
                                            org.apache.camel.Exchange exchange,
                                            java.io.InputStream stream) {
                                        return null;
                                    }

                                    @Override
                                    public void start() {}

                                    @Override
                                    public void stop() {}

                                    @Override
                                    public org.apache.camel.spi.PropertyConfigurer
                                            getPropertyConfigurer(Object instance) {
                                        return (org.apache.camel.CamelContext context1,
                                                Object target,
                                                String propName,
                                                Object value,
                                                boolean ignoreCase) -> true;
                                    }
                                }
                                return new MockDataFormat();
                            });

            // Load routes from YAML
            String cleanContent =
                    vn.cxn.apache_camel.util.CamelYamlParser.stripNonScriptingBeansAndMetadata(
                            yamlContent);
            Resource resource = ResourceHelper.fromString("route.yaml", cleanContent);
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            // Traverse route definitions to extract schemes
            if (context.getRouteDefinitions() != null) {
                for (org.apache.camel.model.RouteDefinition rd : context.getRouteDefinitions()) {
                    if (rd.getInput() != null) {
                        String scheme = extractScheme(rd.getInput().getUri());
                        if (scheme != null) {
                            schemes.add(scheme);
                        }
                    }
                    extractSchemesFromOutputs(rd.getOutputs(), schemes);
                }
            }

            // Traverse REST definitions to extract schemes
            if (context.getRestDefinitions() != null) {
                for (org.apache.camel.model.rest.RestDefinition rest :
                        context.getRestDefinitions()) {
                    schemes.add("rest"); // REST DSL implies rest component usage
                    if (rest.getVerbs() != null) {
                        for (org.apache.camel.model.rest.VerbDefinition verb : rest.getVerbs()) {
                            if (verb.getTo() != null) {
                                String scheme = extractScheme(verb.getTo().getUri());
                                if (scheme != null) {
                                    schemes.add(scheme);
                                }
                            }
                        }
                    }
                }
            }

            // Start context briefly to trigger endpoint/route resolution of remaining items
            context.start();

            // Extract REST configuration properties
            RestConfiguration restConfig = context.getRestConfiguration();
            if (restConfig != null) {
                // If apiContextPath is set, we need camel-openapi-java and rest-api component
                if (restConfig.getApiContextPath() != null) {
                    extraDependencies.add("openapi-java");
                    schemes.add("rest-api");
                }
                // Determine dataformat from bindingMode
                if (restConfig.getBindingMode() != null) {
                    String mode = restConfig.getBindingMode().name().toLowerCase();
                    if (mode.contains("json")) {
                        dataFormats.add("jackson");
                    }
                    if (mode.contains("xml")) {
                        dataFormats.add("jaxb");
                    }
                }
            }

            // Simple static scan fallback for resilience4j / faultTolerance configurations
            if (yamlContent.contains("resilience4jConfiguration")
                    || yamlContent.contains("resilience4j-configuration")) {
                eipHints.add("resilience4jConfiguration");
            }
            if (yamlContent.contains("faultToleranceConfiguration")
                    || yamlContent.contains("fault-tolerance-configuration")) {
                eipHints.add("faultToleranceConfiguration");
            }

        } catch (Exception e) {
            log.warn("Mock CamelContext route parsing/startup failed: {}", e.getMessage());
        } finally {
            try {
                context.stop();
            } catch (Exception ignored) {
            }
        }

        return new ScanResult(
                schemes, eipHints, dataFormats, languages, extraDependencies, explicitDependencies);
    }

    private void parseModelineDependencies(String yamlContent, Set<String> explicitDependencies) {
        if (yamlContent == null) return;
        String[] lines = yamlContent.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            java.util.regex.Matcher matcher = MODELINE_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                String depsPart = matcher.group(1).replaceAll("\\s+", "");
                if (!depsPart.isEmpty()) {
                    String[] deps = depsPart.split(",");
                    for (String dep : deps) {
                        String cleanDep = dep.trim();
                        if (!cleanDep.isEmpty()) {
                            explicitDependencies.add(cleanDep);
                            log.info("Detected modeline dependency: {}", cleanDep);
                        }
                    }
                }
            }
        }
    }

    private String extractScheme(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        int index = uri.indexOf(':');
        if (index > 0) {
            return uri.substring(0, index).trim();
        }
        return null;
    }

    private void extractSchemesFromOutputs(
            java.util.List<org.apache.camel.model.ProcessorDefinition<?>> outputs,
            Set<String> schemes) {
        if (outputs == null) {
            return;
        }
        for (org.apache.camel.model.ProcessorDefinition<?> pd : outputs) {
            if (pd instanceof org.apache.camel.model.ToDefinition to) {
                String scheme = extractScheme(to.getUri());
                if (scheme != null) {
                    schemes.add(scheme);
                }
            } else if (pd instanceof org.apache.camel.model.ToDynamicDefinition toDyn) {
                String scheme = extractScheme(toDyn.getUri());
                if (scheme != null) {
                    schemes.add(scheme);
                }
            }
            extractSchemesFromOutputs(pd.getOutputs(), schemes);
        }
    }

    public record ScanResult(
            Set<String> schemes,
            Set<String> eipHints,
            Set<String> dataFormats,
            Set<String> languages,
            Set<String> extraDependencies,
            Set<String> explicitDependencies) {
        public boolean isEmpty() {
            return schemes.isEmpty()
                    && eipHints.isEmpty()
                    && dataFormats.isEmpty()
                    && languages.isEmpty()
                    && extraDependencies.isEmpty()
                    && explicitDependencies.isEmpty();
        }
    }
}
