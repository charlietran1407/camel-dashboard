package vn.cxn.apache_camel.service;

import java.util.List;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.service.route_validation.CanonicalizationValidationStepImpl;
import vn.cxn.apache_camel.service.route_validation.ConflictValidationStepImpl;
import vn.cxn.apache_camel.service.route_validation.DependencyValidationStepImpl;
import vn.cxn.apache_camel.service.route_validation.RouteValidationContext;
import vn.cxn.apache_camel.service.route_validation.RouteValidationStep;
import vn.cxn.apache_camel.service.route_validation.RuntimeDryRunValidationStepImpl;
import vn.cxn.apache_camel.service.route_validation.SyntaxValidationStepImpl;
import vn.cxn.apache_camel.validation.RouteValidationResult;

@Service
public class RouteValidationService {

    private final List<RouteValidationStep> validationSteps;

    @Autowired
    public RouteValidationService(List<RouteValidationStep> validationSteps) {
        this.validationSteps = validationSteps;
    }

    public RouteValidationService(
            CamelContext camelContext,
            RouteVersionService routeVersionService,
            DynamicBeanService dynamicBeanService,
            EnvPropertyService envPropertyService) {
        this.validationSteps =
                List.of(
                        new SyntaxValidationStepImpl(camelContext),
                        new CanonicalizationValidationStepImpl(routeVersionService),
                        new DependencyValidationStepImpl(
                                camelContext, dynamicBeanService, envPropertyService),
                        new ConflictValidationStepImpl(routeVersionService),
                        new RuntimeDryRunValidationStepImpl());
    }

    public RouteValidationResult validate(
            String serviceId, String fileName, String content, String modeStr) {
        String executionMode =
                (modeStr != null && modeStr.equalsIgnoreCase("PRE_DEPLOY")) ? "PRE_DEPLOY" : "FAST";
        RouteValidationContext context =
                new RouteValidationContext(serviceId, fileName, content, executionMode);
        try {
            for (RouteValidationStep step : validationSteps) {
                if (!step.validate(context)) {
                    break;
                }
            }
            return context.result();
        } finally {
            if (context.isolatedContext() != null) {
                try {
                    context.isolatedContext().stop();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
