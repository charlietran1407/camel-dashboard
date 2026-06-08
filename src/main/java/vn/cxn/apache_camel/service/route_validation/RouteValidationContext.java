package vn.cxn.apache_camel.service.route_validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.camel.impl.DefaultCamelContext;
import vn.cxn.apache_camel.validation.RouteValidationResult;

public class RouteValidationContext {

    private final String serviceId;
    private final String fileName;
    private final String content;
    private final String executionMode;
    private final RouteValidationResult result;
    private DefaultCamelContext isolatedContext;
    private List<String> originalRouteIds = new ArrayList<>();
    private List<String> managedRouteIds = new ArrayList<>();
    private Set<String> targetSignatures = Set.of();

    public RouteValidationContext(
            String serviceId, String fileName, String content, String executionMode) {
        this.serviceId = serviceId;
        this.fileName = fileName;
        this.content = content;
        this.executionMode = executionMode;
        this.result = new RouteValidationResult();
    }

    public String serviceId() {
        return serviceId;
    }

    public String fileName() {
        return fileName;
    }

    public String content() {
        return content;
    }

    public String executionMode() {
        return executionMode;
    }

    public RouteValidationResult result() {
        return result;
    }

    public DefaultCamelContext isolatedContext() {
        return isolatedContext;
    }

    public void setIsolatedContext(DefaultCamelContext isolatedContext) {
        this.isolatedContext = isolatedContext;
    }

    public List<String> originalRouteIds() {
        return originalRouteIds;
    }

    public void setOriginalRouteIds(List<String> originalRouteIds) {
        this.originalRouteIds = originalRouteIds;
    }

    public List<String> managedRouteIds() {
        return managedRouteIds;
    }

    public void setManagedRouteIds(List<String> managedRouteIds) {
        this.managedRouteIds = managedRouteIds;
    }

    public Set<String> targetSignatures() {
        return targetSignatures;
    }

    public void setTargetSignatures(Set<String> targetSignatures) {
        this.targetSignatures = targetSignatures;
    }
}
