package vn.cxn.apache_camel.service.route_validation;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.RouteVersionService;
import vn.cxn.apache_camel.validation.ValidationError;

@Component
@Order(40)
public class ConflictValidationStepImpl implements RouteValidationStep {

    private static final Logger log = LoggerFactory.getLogger(ConflictValidationStepImpl.class);

    private final RouteVersionService routeVersionService;

    public ConflictValidationStepImpl(RouteVersionService routeVersionService) {
        this.routeVersionService = routeVersionService;
    }

    @Override
    public boolean validate(RouteValidationContext context) {
        List<RouteVersion> candidates = routeVersionService.getAllVersions();
        String targetApiContextPathSignature =
                routeVersionService.getApiContextPathSignature(
                        context.fileName(), context.content());
        for (RouteVersion activeVersion : candidates) {
            if (!activeVersion.isAutoRestore()) {
                continue;
            }
            if (Objects.equals(activeVersion.getServiceId(), context.serviceId())) {
                context.result().getReplaceCandidates().add(activeVersion.getId());
                continue;
            }

            validateRouteIdCollisions(context, activeVersion);
            validateSignatureCollisions(context, activeVersion, targetApiContextPathSignature);
        }
        return true;
    }

    private void validateRouteIdCollisions(
            RouteValidationContext context, RouteVersion activeVersion) {
        if (activeVersion.getRouteIds() == null || context.managedRouteIds() == null) {
            return;
        }
        for (String routeId : context.managedRouteIds()) {
            if (activeVersion.getRouteIds().contains(routeId)) {
                context.result().setIsValid(false);
                context.result().setStage("CONFLICT_STAGE");
                String serviceNameOrId =
                        activeVersion.getServiceName() != null
                                ? activeVersion.getServiceName()
                                : activeVersion.getServiceId();
                context.result()
                        .getErrors()
                        .add(
                                new ValidationError(
                                        "CROSS_SERVICE_ROUTE_ID_COLLISION",
                                        "Route ID '"
                                                + routeId
                                                + "' can not deploy has been occupied by Service '"
                                                + serviceNameOrId
                                                + "' ("
                                                + activeVersion.getFileName()
                                                + ").",
                                        null,
                                        List.of(
                                                "Route ID '" + routeId + "'",
                                                serviceNameOrId,
                                                activeVersion.getFileName())));
            }
        }
    }

    private void validateSignatureCollisions(
            RouteValidationContext context,
            RouteVersion activeVersion,
            String targetApiContextPathSignature) {
        try {
            String activeContent = routeVersionService.getContentFromDisk(activeVersion.getId());
            String activeApiContextPathSignature =
                    routeVersionService.getApiContextPathSignature(
                            activeVersion.getFileName(), activeContent);

            Set<String> activeSignatures =
                    routeVersionService.getDeploymentSignatures(activeVersion);
            for (String signature : context.targetSignatures()) {
                if (targetApiContextPathSignature != null
                        && signature.equals(targetApiContextPathSignature)) {
                    continue;
                }
                if (activeApiContextPathSignature != null
                        && signature.equals(activeApiContextPathSignature)) {
                    continue;
                }
                if (activeSignatures.contains(signature)) {
                    addSignatureCollision(context, activeVersion, signature);
                }
            }
        } catch (IOException e) {
            log.warn(
                    "Could not extract active signatures for version '{}': {}",
                    activeVersion.getId(),
                    e.getMessage());
        }
    }

    private void addSignatureCollision(
            RouteValidationContext context, RouteVersion activeVersion, String signature) {
        context.result().setIsValid(false);
        context.result().setStage("CONFLICT_STAGE");

        String friendlyName;
        String code;
        if (signature.startsWith("route-id:")) {
            friendlyName = "Route ID '" + signature.substring(9) + "'";
            code = "CROSS_SERVICE_ROUTE_ID_COLLISION";
        } else if (signature.startsWith("endpoint:")) {
            friendlyName = "Public Endpoint '" + signature.substring(9) + "'";
            code = "CROSS_SERVICE_ENDPOINT_COLLISION";
        } else {
            friendlyName = "REST Path '" + signature.substring(5) + "'";
            code = "CROSS_SERVICE_ENDPOINT_COLLISION";
        }

        String serviceNameOrId =
                activeVersion.getServiceName() != null
                        ? activeVersion.getServiceName()
                        : activeVersion.getServiceId();
        context.result()
                .getErrors()
                .add(
                        new ValidationError(
                                code,
                                "Can not deploy "
                                        + friendlyName
                                        + " has been occupied by Service '"
                                        + serviceNameOrId
                                        + "' ("
                                        + activeVersion.getFileName()
                                        + ").",
                                null,
                                List.of(
                                        friendlyName,
                                        serviceNameOrId,
                                        activeVersion.getFileName())));
    }
}
