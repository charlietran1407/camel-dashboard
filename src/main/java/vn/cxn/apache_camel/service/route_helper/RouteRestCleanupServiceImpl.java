package vn.cxn.apache_camel.service.route_helper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.RouteStateService;
import vn.cxn.apache_camel.service.RouteVersionService;
import vn.cxn.apache_camel.util.CamelRouteUtil;
import vn.cxn.apache_camel.util.CamelYamlParser;

@Service
public class RouteRestCleanupServiceImpl implements RouteRestCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RouteRestCleanupServiceImpl.class);

    private final CamelContext camelContext;
    private final RouteVersionService versionService;
    private final RouteStateService routeStateService;
    private final RouteEndpointCleanupService routeEndpointCleanupService;

    public RouteRestCleanupServiceImpl(
            CamelContext camelContext,
            RouteVersionService versionService,
            RouteStateService routeStateService,
            RouteEndpointCleanupService routeEndpointCleanupService) {
        this.camelContext = camelContext;
        this.versionService = versionService;
        this.routeStateService = routeStateService;
        this.routeEndpointCleanupService = routeEndpointCleanupService;
    }

    @Override
    @Transactional
    public void cleanUpRestDefinitions(String serviceId, Collection<String> routeIds) {
        try {
            if (!(camelContext instanceof ModelCamelContext modelContext)) {
                return;
            }
            var restDefinitions = modelContext.getRestDefinitions();
            if (restDefinitions == null || restDefinitions.isEmpty()) {
                return;
            }

            // 1. Static extraction of REST paths from disk
            var serviceRestPaths = new HashSet<String>();
            var versionsToParse = new ArrayList<RouteVersion>();
            if (serviceId != null && !serviceId.isBlank()) {
                versionsToParse.addAll(versionService.getVersionsByServiceId(serviceId));
            }
            if (routeIds != null) {
                for (var rId : routeIds) {
                    if (rId != null) {
                        versionsToParse.addAll(versionService.getVersionsByRouteId(rId));
                    }
                }
            }

            for (var rv : versionsToParse) {
                try {
                    var yamlContent = versionService.getContentFromDisk(rv.getId());
                    if (yamlContent != null && !yamlContent.isBlank()) {
                        serviceRestPaths.addAll(
                                CamelYamlParser.extractRestPathsFromYaml(yamlContent));
                    }
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }

            // 2. Dynamic extraction from context
            var servicePrefix = CamelRouteUtil.getServicePrefix(serviceId);
            var allRouteIds =
                    routeIds != null
                            ? routeIds.stream().filter(Objects::nonNull).collect(Collectors.toSet())
                            : Collections.<String>emptySet();

            for (var rest : restDefinitions) {
                if (rest.getVerbs() != null) {
                    for (var verb : rest.getVerbs()) {
                        if (CamelRouteUtil.verbBelongsToService(
                                        verb, serviceId, servicePrefix, allRouteIds)
                                && rest.getPath() != null) {
                            serviceRestPaths.add(rest.getPath());
                        }
                    }
                }
            }

            log.info(
                    "Cleaning up REST definitions for serviceId: {}, routeIds: {},"
                            + " serviceRestPaths: {}",
                    serviceId,
                    allRouteIds,
                    serviceRestPaths);

            var restRouteIdsToRemove = new LinkedHashSet<String>();

            // 3. Remove matching VerbDefinitions and RestDefinitions
            var restIterator = restDefinitions.iterator();
            while (restIterator.hasNext()) {
                var rest = restIterator.next();
                boolean removeEntireRest =
                        serviceId != null
                                && !serviceId.isBlank()
                                && CamelRouteUtil.pathMatchesAny(rest.getPath(), serviceRestPaths);

                if (rest.getVerbs() != null) {
                    var verbIterator = rest.getVerbs().iterator();
                    while (verbIterator.hasNext()) {
                        var verb = verbIterator.next();
                        if (removeEntireRest
                                || CamelRouteUtil.verbBelongsToService(
                                        verb, serviceId, servicePrefix, allRouteIds)) {
                            if (verb.getRouteId() != null) {
                                restRouteIdsToRemove.add(verb.getRouteId());
                            }
                            log.info(
                                    "Removing VerbDefinition '{}' (routeId: {}) from RestDefinition"
                                            + " '{}'",
                                    verb.getClass().getSimpleName().replace("Definition", ""),
                                    verb.getRouteId(),
                                    rest.getPath());
                            verbIterator.remove();
                        }
                    }
                }

                if (removeEntireRest || rest.getVerbs() == null || rest.getVerbs().isEmpty()) {
                    log.info("Removing RestDefinition for path '{}'", rest.getPath());
                    if (rest.getVerbs() != null) {
                        for (var verb : rest.getVerbs()) {
                            if (verb.getRouteId() != null) {
                                restRouteIdsToRemove.add(verb.getRouteId());
                            }
                        }
                    }
                    restIterator.remove();
                }
            }

            // 4. Scan live routes to find matching REST consumer routes
            for (var route : camelContext.getRoutes()) {
                if (route.getEndpoint() != null) {
                    var uri = route.getEndpoint().getEndpointUri();
                    if (uri != null
                            && (uri.startsWith("rest://")
                                    || uri.startsWith("rest:")
                                    || uri.startsWith("rest-api://")
                                    || uri.startsWith("rest-api:")
                                    || uri.startsWith("rest-openapi://")
                                    || uri.startsWith("rest-openapi:"))) {
                        var pathPart = CamelRouteUtil.extractPathFromRestUri(uri);
                        if (CamelRouteUtil.pathMatchesAny(pathPart, serviceRestPaths)) {
                            restRouteIdsToRemove.add(route.getId());
                        }
                    }
                }
            }

            // 5. Stop and remove the identified REST consumer routes from CamelContext
            for (var rId : restRouteIdsToRemove) {
                if (camelContext.getRoute(rId) != null) {
                    log.info(
                            "Stopping and removing associated REST route '{}' from CamelContext",
                            rId);
                    try {
                        camelContext.getRouteController().stopRoute(rId);
                        routeEndpointCleanupService.cleanUpEndpointsForRoute(rId);
                        camelContext.removeRoute(rId);
                        routeStateService.removeDesiredState(rId);
                    } catch (Exception e) {
                        log.warn(
                                "Failed to cleanly stop/remove REST route '{}': {}",
                                rId,
                                e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to clean up REST definitions for service '{}': {}",
                    serviceId,
                    e.getMessage(),
                    e);
        }
    }

    @Override
    @Transactional
    public void cleanUpRestDefinitions(String routeId) {
        cleanUpRestDefinitions(null, Collections.singletonList(routeId));
    }
}
