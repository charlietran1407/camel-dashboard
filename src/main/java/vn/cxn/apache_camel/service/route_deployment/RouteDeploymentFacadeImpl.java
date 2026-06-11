package vn.cxn.apache_camel.service.route_deployment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.entity.RouteVersionEntity;
import vn.cxn.apache_camel.repository.RouteVersionRepository;
import vn.cxn.apache_camel.service.RouteStateService;
import vn.cxn.apache_camel.service.RouteValidationService;
import vn.cxn.apache_camel.service.RouteVersionService;
import vn.cxn.apache_camel.service.component.LoaderPathDependencyService;
import vn.cxn.apache_camel.service.component.MissingComponentDownloadResult;
import vn.cxn.apache_camel.service.route_helper.*;
import vn.cxn.apache_camel.util.CamelRouteUtil;
import vn.cxn.apache_camel.util.CamelYamlParser;
import vn.cxn.apache_camel.validation.MissingComponentsDownloadedException;
import vn.cxn.apache_camel.validation.PreDeployValidationException;
import vn.cxn.apache_camel.validation.RouteValidationResult;

@Component
public class RouteDeploymentFacadeImpl implements RouteDeploymentFacade {

    private static final Logger log = LoggerFactory.getLogger(RouteDeploymentFacadeImpl.class);

    private final CamelContext camelContext;
    private final RouteVersionService versionService;
    private final RouteStateService routeStateService;
    private final RouteValidationService validationService;
    private final RouteVersionRepository routeVersionRepository;
    private final LoaderPathDependencyService loaderPathDependencyService;

    private final RouteRestCleanupService routeRestCleanupService;
    private final RouteEndpointCleanupService routeEndpointCleanupService;
    private final RouteRegistrationService routeRegistrationService;
    private final RouteVersionContentHelper routeVersionContentHelper;
    private final AuditLogger auditLogger;

    public RouteDeploymentFacadeImpl(
            CamelContext camelContext,
            RouteVersionService versionService,
            RouteStateService routeStateService,
            RouteValidationService validationService,
            RouteVersionRepository routeVersionRepository,
            LoaderPathDependencyService loaderPathDependencyService,
            RouteRestCleanupService routeRestCleanupService,
            RouteEndpointCleanupService routeEndpointCleanupService,
            RouteRegistrationService routeRegistrationService,
            RouteVersionContentHelper routeVersionContentHelper,
            AuditLogger auditLogger) {
        this.camelContext = camelContext;
        this.versionService = versionService;
        this.routeStateService = routeStateService;
        this.validationService = validationService;
        this.routeVersionRepository = routeVersionRepository;
        this.loaderPathDependencyService = loaderPathDependencyService;
        this.routeRestCleanupService = routeRestCleanupService;
        this.routeEndpointCleanupService = routeEndpointCleanupService;
        this.routeRegistrationService = routeRegistrationService;
        this.routeVersionContentHelper = routeVersionContentHelper;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional
    public String deployFromVersion(String versionId) throws Exception {
        Optional<RouteVersion> versionOpt = versionService.getVersionById(versionId);
        if (versionOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found: " + versionId);
        }

        RouteVersion rv = versionOpt.get();
        Optional<RouteVersionEntity> rveOpt = routeVersionRepository.findById(parseUuid(versionId));
        if (rveOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found in DB: " + versionId);
        }

        String primaryId =
                (rv.getRouteIds() != null && !rv.getRouteIds().isEmpty())
                        ? rv.getRouteIds().get(0)
                        : rv.getFileName();

        try {
            String yamlContent = routeVersionContentHelper.readDeploymentContent(rv);
            String resourceName = routeVersionContentHelper.normalizeResourceName(rv.getFileName());
            String serviceId = rv.getServiceId();
            Set<String> idsToClean = collectRouteIdsToClean(rv, serviceId);
            List<RouteVersion> groupVersions =
                    (serviceId != null)
                            ? versionService.getVersionsByServiceId(serviceId)
                            : new ArrayList<>();

            /*
             * Component auto-download (loader.path strategy)
             * Scan YAML for missing Camel components. If any are found they are downloaded
             * to the configured loader.path dir. A restart is required to activate them;
             * we throw immediately so the controller can return HTTP 202 to the client.
             */
            MissingComponentDownloadResult componentResult =
                    loaderPathDependencyService.ensureComponentsAvailable(yamlContent);
            if (componentResult.isRestartRequired()) {
                log.warn(
                        "Deployment of version {} deferred: missing components downloaded — "
                                + "restart required. Artifacts: {}",
                        versionId,
                        componentResult.getDownloadedArtifacts());
                throw new MissingComponentsDownloadedException(
                        componentResult.getDownloadedArtifacts(),
                        loaderPathDependencyService.getLoaderPath());
            }

            validatePreDeploy(rv, yamlContent);
            Map<String, String> rollbackSnapshots = snapshotRollbackVersions(groupVersions);
            removeExistingRoutes(serviceId, idsToClean);
            clearPersistedStates(serviceId);
            loadRoutes(versionId, rv, primaryId, resourceName, yamlContent, rollbackSnapshots);
            markDeploymentSuccessful(versionId, rv, primaryId);
            return primaryId;
        } catch (MissingComponentsDownloadedException e) {
            // Propagate without logging as a failure — this is an expected
            // deferred-activation path
            throw e;
        } catch (PreDeployValidationException e) {
            logDeploymentFailure(rv, e);
            throw e;
        } catch (Exception e) {
            logDeploymentFailure(rv, e);
            throw e;
        }
    }

    private Set<String> collectRouteIdsToClean(RouteVersion rv, String serviceId) {
        Set<String> idsToClean = new LinkedHashSet<>();
        if (rv.getRouteIds() != null) {
            idsToClean.addAll(rv.getRouteIds());
        }

        List<RouteVersion> groupVersions =
                (serviceId != null)
                        ? versionService.getVersionsByServiceId(serviceId)
                        : new ArrayList<>();
        groupVersions.forEach(
                old -> {
                    if (old.getRouteIds() != null) {
                        idsToClean.addAll(old.getRouteIds());
                    }
                });

        if (serviceId != null && !serviceId.isBlank()) {
            String managedPrefix = CamelRouteUtil.getServicePrefix(serviceId);
            camelContext.getRoutes().stream()
                    .map(Route::getId)
                    .filter(id -> id != null && id.startsWith(managedPrefix))
                    .forEach(idsToClean::add);
        }
        return idsToClean;
    }

    private void validatePreDeploy(RouteVersion rv, String yamlContent) {
        RouteValidationResult valResult =
                validationService.validate(
                        rv.getServiceId(), rv.getFileName(), yamlContent, "PRE_DEPLOY");
        if (!valResult.getIsValid()) {
            throw new PreDeployValidationException(valResult);
        }
    }

    private Map<String, String> snapshotRollbackVersions(List<RouteVersion> groupVersions) {
        Map<String, String> rollbackSnapshots = new LinkedHashMap<>();
        for (RouteVersion old : groupVersions) {
            if (old.isAutoRestore()) {
                try {
                    String oldYaml = versionService.getDeploymentContent(old);
                    rollbackSnapshots.put(old.getId(), oldYaml);
                } catch (Exception e) {
                    log.warn(
                            "Could not snapshot old version '{}' for rollback: {}",
                            old.getId(),
                            e.getMessage());
                }
            }
        }
        return rollbackSnapshots;
    }

    private void removeExistingRoutes(String serviceId, Set<String> idsToClean) {
        routeRestCleanupService.cleanUpRestDefinitions(serviceId, idsToClean);
        for (String id : idsToClean) {
            if (id != null && camelContext.getRoute(id) != null) {
                log.info("Removing route '{}' from context before re-deployment", id);
                try {
                    camelContext.getRouteController().stopRoute(id);
                    camelContext.removeRoute(id);
                    routeEndpointCleanupService.cleanUpEndpointsForRoute(id);
                } catch (Exception e) {
                    log.warn("Failed to remove route '{}': {}", id, e.getMessage());
                }
            }
        }
    }

    private void clearPersistedStates(String serviceId) {
        if (serviceId != null && !serviceId.isBlank()) {
            String managedPrefix = CamelRouteUtil.getServicePrefix(serviceId);
            routeStateService.removeStatesWithPrefix(managedPrefix);
            log.info("Cleared persisted route states for prefix '{}'", managedPrefix);
        }
    }

    private void loadRoutes(
            String versionId,
            RouteVersion rv,
            String primaryId,
            String resourceName,
            String yamlContent,
            Map<String, String> rollbackSnapshots)
            throws Exception {
        log.info(
                "Deploying version {} for route group {} from file {}",
                rv.getVersion(),
                primaryId,
                resourceName);
        log.debug(
                "YAML content (first 200 chars): {}",
                yamlContent.substring(0, Math.min(200, yamlContent.length())));

        try {
            String cleanContent = CamelYamlParser.stripMetadata(yamlContent);
            Resource resource = ResourceHelper.fromString(resourceName, cleanContent);
            PluginHelper.getRoutesLoader(camelContext).loadRoutes(resource);
            routeRegistrationService.registerRoutesInDb(rv);
        } catch (Exception e) {
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("Failed to load route from YAML for version {}: {}", versionId, cause, e);
            rollback(rollbackSnapshots);
            throw new IllegalArgumentException(
                    "YAML parse/load error for '"
                            + rv.getFileName()
                            + "': "
                            + cause
                            + ". Please verify the YAML syntax (check for unescaped quotes,"
                            + " indentation, etc.).",
                    e);
        }
    }

    private void rollback(Map<String, String> rollbackSnapshots) {
        if (rollbackSnapshots.isEmpty()) {
            return;
        }
        log.warn(
                "Attempting rollback of {} snapshot(s) after failed load",
                rollbackSnapshots.size());
        for (Map.Entry<String, String> snap : rollbackSnapshots.entrySet()) {
            try {
                Optional<RouteVersion> snapRv = versionService.getVersionById(snap.getKey());
                if (snapRv.isPresent()) {
                    String snapFile =
                            routeVersionContentHelper.normalizeResourceName(
                                    snapRv.get().getFileName());
                    String cleanRollbackContent = CamelYamlParser.stripMetadata(snap.getValue());
                    Resource rollbackResource =
                            ResourceHelper.fromString(snapFile, cleanRollbackContent);
                    PluginHelper.getRoutesLoader(camelContext).loadRoutes(rollbackResource);
                    log.info("Rollback succeeded for version '{}'", snap.getKey());
                }
            } catch (Exception e) {
                log.error(
                        "Rollback also failed for version '{}': {}", snap.getKey(), e.getMessage());
            }
        }
    }

    private void markDeploymentSuccessful(String versionId, RouteVersion rv, String primaryId) {
        versionService.markAsAutoRestore(versionId);
        versionService.updateDeployedAt(versionId, Instant.now());

        log.info("Deployed route group {} version {}", primaryId, rv.getVersion());
        auditLogger.logDeploymentSuccess(rv, primaryId);
    }

    private void logDeploymentFailure(RouteVersion rv, PreDeployValidationException e) {
        String errorMsg = "Pre-deploy validation failed";
        if (e.getValidationResult() != null
                && e.getValidationResult().getErrors() != null
                && !e.getValidationResult().getErrors().isEmpty()) {
            errorMsg =
                    e.getValidationResult().getErrors().stream()
                            .map(err -> err.getMessage() != null ? err.getMessage() : err.getCode())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining("; "));
        } else if (e.getMessage() != null) {
            errorMsg = e.getMessage();
        }
        auditLogger.logDeploymentFailure(rv, errorMsg);
    }

    private void logDeploymentFailure(RouteVersion rv, Exception e) {
        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        auditLogger.logDeploymentFailure(rv, errorMsg);
    }

    private UUID parseUuid(String str) {
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(str.getBytes());
        }
    }
}
