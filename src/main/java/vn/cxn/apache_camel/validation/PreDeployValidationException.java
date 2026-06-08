package vn.cxn.apache_camel.validation;

public class PreDeployValidationException extends IllegalArgumentException {
    private final RouteValidationResult validationResult;

    public PreDeployValidationException(RouteValidationResult validationResult) {
        super("PRE_DEPLOY_FAILED");
        this.validationResult = validationResult;
    }

    public RouteValidationResult getValidationResult() {
        return validationResult;
    }
}
