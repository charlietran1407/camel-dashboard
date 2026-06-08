package vn.cxn.apache_camel.service.route_helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.SystemLogService;

@Service
public class AuditLoggerImpl implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggerImpl.class);

    private final SystemLogService systemLogService;

    public AuditLoggerImpl(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @Override
    public void logRouteAction(String routeId, String action) {
        try {
            systemLogService.log(
                    "AUDIT", "SUCCESS", routeId, action + " Route: " + routeId, null, null, null);
        } catch (Exception e) {
            log.warn("Failed to audit {} Route log: {}", action, e.getMessage());
        }
    }

    @Override
    public void logDeploymentSuccess(RouteVersion rv, String primaryId) {
        systemLogService.log(
                "DEPLOY",
                "SUCCESS",
                rv.getServiceId(),
                "Deployed route group " + primaryId + " version " + rv.getVersion(),
                rv.getVersion(),
                rv.getId(),
                rv.getFileName());

        try {
            systemLogService.log(
                    "AUDIT",
                    "SUCCESS",
                    rv.getServiceId(),
                    "Deployed route group " + primaryId + " version " + rv.getVersion(),
                    rv.getVersion(),
                    rv.getId(),
                    rv.getFileName());
        } catch (Exception e) {
            log.warn("Failed to audit route deployment log: {}", e.getMessage());
        }
    }

    @Override
    public void logDeploymentFailure(RouteVersion rv, String errorMsg) {
        systemLogService.log(
                "DEPLOY",
                "FAILED",
                rv.getServiceId(),
                errorMsg,
                rv.getVersion(),
                rv.getId(),
                rv.getFileName());
    }
}
