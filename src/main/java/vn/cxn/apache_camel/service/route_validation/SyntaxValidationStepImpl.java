package vn.cxn.apache_camel.service.route_validation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.validation.ValidationError;

@Component
@Order(10)
public class SyntaxValidationStepImpl implements RouteValidationStep {

    private static final Logger log = LoggerFactory.getLogger(SyntaxValidationStepImpl.class);

    private final CamelContext camelContext;

    public SyntaxValidationStepImpl(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean validate(RouteValidationContext context) {
        DefaultCamelContext isolatedContext = new DefaultCamelContext();
        context.setIsolatedContext(isolatedContext);
        try {
            camelContext
                    .getRegistry()
                    .findByTypeWithName(Object.class)
                    .forEach(
                            (name, bean) -> {
                                try {
                                    isolatedContext.getRegistry().bind(name, bean);
                                } catch (Exception ignored) {
                                }
                            });
            isolatedContext.setPropertiesComponent(camelContext.getPropertiesComponent());
        } catch (Exception e) {
            log.warn(
                    "Could not share context configuration to isolatedContext: {}", e.getMessage());
        }

        try {
            Resource resource = ResourceHelper.fromString(context.fileName(), context.content());
            PluginHelper.getRoutesLoader(isolatedContext).loadRoutes(resource);
            return true;
        } catch (Exception e) {
            String msg = e.getMessage();
            context.result().setIsValid(false);
            context.result().setStage("SYNTAX_STAGE");
            context.result()
                    .getErrors()
                    .add(
                            new ValidationError(
                                    "DSL_PARSE_ERROR",
                                    "Can not parse DSL file " + context.fileName() + ": " + msg,
                                    extractLocation(e),
                                    List.of(msg)));
            return false;
        }
    }

    private String extractLocation(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getClass().getName().contains("MarkedYAMLException")) {
                try {
                    Object mark = cause.getClass().getMethod("getProblemMark").invoke(cause);
                    if (mark != null) {
                        int line = (int) mark.getClass().getMethod("getLine").invoke(mark);
                        int column = (int) mark.getClass().getMethod("getColumn").invoke(mark);
                        return "line " + (line + 1) + ", column " + (column + 1);
                    }
                } catch (Exception ignored) {
                }
            }

            String msg = cause.getMessage();
            if (msg != null) {
                Matcher matcher = Pattern.compile("line (\\d+)(?:, column (\\d+))?").matcher(msg);
                if (matcher.find()) {
                    String line = matcher.group(1);
                    String col = matcher.group(2);
                    return "line " + line + (col != null ? ", column " + col : "");
                }
            }

            cause = cause.getCause();
        }
        return null;
    }
}
