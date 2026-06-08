package vn.cxn.apache_camel.service.route_validation;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.service.RouteVersionService;
import vn.cxn.apache_camel.validation.ValidationError;

@Component
@Order(20)
public class CanonicalizationValidationStepImpl implements RouteValidationStep {

    private final RouteVersionService routeVersionService;

    public CanonicalizationValidationStepImpl(RouteVersionService routeVersionService) {
        this.routeVersionService = routeVersionService;
    }

    @Override
    public boolean validate(RouteValidationContext context) {
        context.setOriginalRouteIds(
                routeVersionService.extractRouteIds(context.fileName(), context.content()));
        context.setManagedRouteIds(
                routeVersionService.toManagedRouteIds(
                        context.serviceId(), context.originalRouteIds()));
        try {
            context.setTargetSignatures(
                    routeVersionService.getDeploymentSignatures(
                            context.fileName(), context.content()));
            return true;
        } catch (Exception e) {
            context.result().setIsValid(false);
            context.result().setStage("CANONICALIZATION_STAGE");
            context.result()
                    .getErrors()
                    .add(
                            new ValidationError(
                                    "CANONICALIZATION_ERROR",
                                    "Error in canonicalization and resource signature extraction: "
                                            + e.getMessage(),
                                    null,
                                    List.of(e.getMessage())));
            return false;
        }
    }
}
