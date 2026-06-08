package vn.cxn.apache_camel.service.route_validation;

public interface RouteValidationStep {

    boolean validate(RouteValidationContext context);
}
