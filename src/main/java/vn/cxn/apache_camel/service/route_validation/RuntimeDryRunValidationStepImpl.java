package vn.cxn.apache_camel.service.route_validation;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.validation.ValidationError;

@Component
@Order(50)
public class RuntimeDryRunValidationStepImpl implements RouteValidationStep {

    @Override
    public boolean validate(RouteValidationContext context) {
        if (!context.executionMode().equalsIgnoreCase("PRE_DEPLOY")
                || !context.result().getIsValid()) {
            return true;
        }
        try {
            context.isolatedContext().start();
            return true;
        } catch (Exception e) {
            context.result().setIsValid(false);
            context.result().setStage("RUNTIME_DRY_RUN_STAGE");
            context.result()
                    .getErrors()
                    .add(
                            new ValidationError(
                                    "RUNTIME_DRY_RUN_ERROR",
                                    e.getMessage(),
                                    null,
                                    List.of(e.getMessage())));
            return false;
        }
    }
}
