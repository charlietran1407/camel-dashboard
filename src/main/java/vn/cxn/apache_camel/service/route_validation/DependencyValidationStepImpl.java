package vn.cxn.apache_camel.service.route_validation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.CamelContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.service.DynamicBeanService;
import vn.cxn.apache_camel.service.EnvPropertyService;
import vn.cxn.apache_camel.validation.ValidationWarning;

@Component
@Order(30)
public class DependencyValidationStepImpl implements RouteValidationStep {

    private static final Set<String> IGNORE_BEANS =
            Set.of(
                    "body",
                    "header",
                    "headers",
                    "exchange",
                    "exception",
                    "camelContext",
                    "registry",
                    "properties",
                    "true",
                    "false",
                    "null",
                    "get",
                    "post",
                    "put",
                    "delete",
                    "patch",
                    "options",
                    "head",
                    "trace",
                    "connect",
                    "GET",
                    "POST",
                    "PUT",
                    "DELETE",
                    "PATCH",
                    "OPTIONS",
                    "HEAD",
                    "TRACE",
                    "CONNECT");

    private final CamelContext camelContext;
    private final DynamicBeanService dynamicBeanService;
    private final EnvPropertyService envPropertyService;

    public DependencyValidationStepImpl(
            CamelContext camelContext,
            DynamicBeanService dynamicBeanService,
            EnvPropertyService envPropertyService) {
        this.camelContext = camelContext;
        this.dynamicBeanService = dynamicBeanService;
        this.envPropertyService = envPropertyService;
    }

    @Override
    public boolean validate(RouteValidationContext context) {
        validateProperties(context);
        validateBeans(context);
        return true;
    }

    private void validateProperties(RouteValidationContext context) {
        Set<String> usedProperties = new LinkedHashSet<>();
        Matcher propMatcher =
                Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)\\}\\}").matcher(context.content());
        while (propMatcher.find()) {
            usedProperties.add(propMatcher.group(1));
        }
        for (String prop : usedProperties) {
            boolean exists =
                    envPropertyService.get(prop) != null
                            || System.getProperty(prop) != null
                            || System.getenv(prop) != null;
            if (!exists) {
                try {
                    exists =
                            camelContext.getPropertiesComponent().resolveProperty(prop).isPresent();
                } catch (Exception ignored) {
                }
            }
            if (!exists) {
                context.result()
                        .getWarnings()
                        .add(
                                new ValidationWarning(
                                        "MISSING_PROPERTY_REFERENCE",
                                        "Can not found property '" + prop + "' in current system.",
                                        List.of(prop)));
            }
        }
    }

    private void validateBeans(RouteValidationContext context) {
        Set<String> usedBeans = new LinkedHashSet<>();
        collectMatches("ref:[ \\t]*[\"']?([a-zA-Z0-9_-]+)[\"']?", context.content(), usedBeans);
        collectMatches("bean:[ \\t]*[\"']?([a-zA-Z0-9_-]+)[\"']?", context.content(), usedBeans);
        collectMatches("bean:([a-zA-Z0-9_-]+)", context.content(), usedBeans);
        collectMatches("method:[ \\t]*[\"']?([a-zA-Z0-9_-]+)[\"']?", context.content(), usedBeans);

        for (String bean : usedBeans) {
            if (IGNORE_BEANS.contains(bean)) {
                continue;
            }
            boolean exists =
                    camelContext.getRegistry().lookupByName(bean) != null
                            || dynamicBeanService.getAllBeans().stream()
                                    .anyMatch(b -> b.getBeanName().equals(bean));
            if (!exists) {
                context.result()
                        .getWarnings()
                        .add(
                                new ValidationWarning(
                                        "MISSING_STATIC_BEAN_REFERENCE",
                                        "Can not found bean '"
                                                + bean
                                                + "' in current system registry. The route may be"
                                                + " failed if this bean is not loaded at runtime.",
                                        List.of(bean)));
            }
        }
    }

    private void collectMatches(String pattern, String content, Set<String> matches) {
        Matcher matcher = Pattern.compile(pattern).matcher(content);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
    }
}
