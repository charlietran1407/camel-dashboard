package vn.cxn.apache_camel.service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.RestParamInfo;
import vn.cxn.apache_camel.model.dto.RouteInfo;
import vn.cxn.apache_camel.model.dto.RouteMetadata;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.dto.ServiceDTO;
import vn.cxn.apache_camel.model.entity.ClusterNodeEntity;
import vn.cxn.apache_camel.model.entity.RouteRuntimeStateEntity;
import vn.cxn.apache_camel.model.entity.RouteVersionEntity;
import vn.cxn.apache_camel.model.enums.NodeState;
import vn.cxn.apache_camel.model.enums.RouteState;
import vn.cxn.apache_camel.repository.RouteRepository;
import vn.cxn.apache_camel.repository.RouteVersionRepository;
import vn.cxn.apache_camel.service.route_deployment.RouteDeploymentFacade;
import vn.cxn.apache_camel.service.route_helper.*;
import vn.cxn.apache_camel.util.CamelYamlParser;

@Service
@Transactional(readOnly = true)
public class CamelRouteService implements RouteLifecycleService, RouteQueryService {

    private static final Logger log = LoggerFactory.getLogger(CamelRouteService.class);

    private final CamelContext camelContext;
    private final RouteVersionService versionService;
    private final RouteStateService routeStateService;
    private final RouteRepository routeRepository;
    private final RouteVersionRepository routeVersionRepository;
    private final vn.cxn.apache_camel.repository.ServiceRepository serviceRepository;
    private final ClusterNodeService clusterNodeService;
    private final RouteDeploymentFacade routeDeploymentFacade;

    private final RouteMetadataExtractor routeMetadataExtractor;
    private final RestParamExtractor restParamExtractor;
    private final RouteNodeStateProvider routeNodeStateProvider;
    private final RouteRegistrationService routeRegistrationService;
    private final RouteRestCleanupService routeRestCleanupService;
    private final RouteEndpointCleanupService routeEndpointCleanupService;
    private final AuditLogger auditLogger;

    public CamelRouteService(
            CamelContext camelContext,
            RouteVersionService versionService,
            RouteStateService routeStateService,
            RouteRepository routeRepository,
            RouteVersionRepository routeVersionRepository,
            vn.cxn.apache_camel.repository.ServiceRepository serviceRepository,
            ClusterNodeService clusterNodeService,
            RouteDeploymentFacade routeDeploymentFacade,
            RouteMetadataExtractor routeMetadataExtractor,
            RestParamExtractor restParamExtractor,
            RouteNodeStateProvider routeNodeStateProvider,
            RouteRegistrationService routeRegistrationService,
            RouteRestCleanupService routeRestCleanupService,
            RouteEndpointCleanupService routeEndpointCleanupService,
            AuditLogger auditLogger) {
        this.camelContext = camelContext;
        this.versionService = versionService;
        this.routeStateService = routeStateService;
        this.routeRepository = routeRepository;
        this.routeVersionRepository = routeVersionRepository;
        this.serviceRepository = serviceRepository;
        this.clusterNodeService = clusterNodeService;
        this.routeDeploymentFacade = routeDeploymentFacade;
        this.routeMetadataExtractor = routeMetadataExtractor;
        this.restParamExtractor = restParamExtractor;
        this.routeNodeStateProvider = routeNodeStateProvider;
        this.routeRegistrationService = routeRegistrationService;
        this.routeRestCleanupService = routeRestCleanupService;
        this.routeEndpointCleanupService = routeEndpointCleanupService;
        this.auditLogger = auditLogger;
    }

    /**
     * Restore a specific route version by validating conflicts, loading routes, registering in DB,
     * and applying states.
     */
    void restoreRouteVersion(RouteVersion version, List<RouteVersion> restoredVersions)
            throws Exception {
        validateNoDeploymentConflict(version, restoredVersions);
        String content = versionService.getDeploymentContent(version);
        String resourceName = version.getFileName();
        String cleanContent = CamelYamlParser.stripMetadata(content);
        Resource resource = ResourceHelper.fromString(resourceName, cleanContent);
        PluginHelper.getRoutesLoader(camelContext).loadRoutes(resource);
        routeRegistrationService.registerRoutesInDb(version);
        applyPersistedRouteStates(version);
    }

    /** List all running routes with their status and version info. */
    public List<RouteInfo> listRoutes() {
        List<ClusterNodeEntity> onlineNodes = getOnlineNodes();
        List<RouteRuntimeStateEntity> allRuntimeStates = clusterNodeService.getAllRouteStates();

        Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode =
                allRuntimeStates.stream()
                        .collect(
                                Collectors.groupingBy(
                                        RouteRuntimeStateEntity::getRouteId,
                                        Collectors.toMap(
                                                RouteRuntimeStateEntity::getInstanceId,
                                                state -> state,
                                                (s1, s2) -> s1)));

        return camelContext.getRoutes().stream()
                .map(route -> buildRouteInfo(route, onlineNodes, statesByRouteAndNode))
                .collect(Collectors.toList());
    }

    private RouteInfo buildRouteInfo(
            Route route,
            List<ClusterNodeEntity> onlineNodes,
            Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode) {
        String id = route.getId();
        String sourceUri = route.getEndpoint() != null ? route.getEndpoint().getEndpointUri() : "";
        String defaultDescription = route.getDescription();
        String status = getRouteStatus(id);

        // 1. Extract metadata from DB / disk fallback
        RouteMetadata meta = routeMetadataExtractor.extractRouteMetadata(id, defaultDescription);

        // 2. Extract REST params dynamically
        List<RestParamInfo> restParams =
                restParamExtractor.extractRestParams(id, meta.originalId(), sourceUri);

        // 3. Fallback to parse REST params from YAML on disk if empty
        if (restParams.isEmpty()) {
            restParams =
                    restParamExtractor.extractFallbackRestParams(id, meta.originalId(), sourceUri);
        }

        // 4. Get node states for the route
        List<Map<String, Object>> nodeStates =
                routeNodeStateProvider.getNodeStatesForRoute(id, onlineNodes, statesByRouteAndNode);

        return new RouteInfo(
                id,
                meta.originalId(),
                meta.serviceId(),
                status,
                meta.description(),
                sourceUri,
                meta.activeVersion(),
                meta.totalVersions(),
                meta.persistent(),
                meta.createdAt(),
                meta.updatedAt(),
                meta.deployedAt(),
                restParams,
                nodeStates);
    }

    void validateNoDeploymentConflict(RouteVersion target, List<RouteVersion> candidates)
            throws Exception {
        Set<String> targetSignatures = versionService.getDeploymentSignatures(target);
        if (targetSignatures.isEmpty()) {
            return;
        }

        List<String> conflicts = new ArrayList<>();
        for (RouteVersion activeVersion : candidates) {
            if (!activeVersion.isAutoRestore()) {
                continue;
            }
            if (Objects.equals(activeVersion.getId(), target.getId())
                    || Objects.equals(activeVersion.getServiceId(), target.getServiceId())) {
                continue;
            }

            Set<String> activeSignatures = versionService.getDeploymentSignatures(activeVersion);
            activeSignatures.stream()
                    .filter(targetSignatures::contains)
                    .map(
                            signature ->
                                    signature
                                            + " already used by service "
                                            + activeVersion.getServiceId()
                                            + " ("
                                            + activeVersion.getFileName()
                                            + ")")
                    .forEach(conflicts::add);
        }

        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Deployment conflict: " + String.join("; ", conflicts));
        }
    }

    void applyPersistedRouteStates(RouteVersion version) {
        if (version.getRouteIds() == null) {
            return;
        }
        version.getRouteIds()
                .forEach(
                        routeId ->
                                findPersistedRouteState(version, routeId)
                                        .ifPresent(
                                                state -> {
                                                    try {
                                                        applyRouteState(routeId, state);
                                                    } catch (Exception e) {
                                                        log.warn(
                                                                "Failed to restore persisted state"
                                                                    + " '{}' for route '{}': {}",
                                                                state,
                                                                routeId,
                                                                e.getMessage());
                                                    }
                                                }));
    }

    private Optional<String> findPersistedRouteState(RouteVersion version, String managedRouteId) {
        Optional<String> managedState = routeStateService.getDesiredState(managedRouteId);
        if (managedState.isPresent()) {
            return managedState;
        }
        String originalRouteId = versionService.getOriginalRouteId(version, managedRouteId);
        Optional<String> legacyState = routeStateService.getDesiredState(originalRouteId);
        legacyState.ifPresent(state -> routeStateService.saveDesiredState(managedRouteId, state));
        return legacyState;
    }

    private void applyRouteState(String routeId, String state) throws Exception {
        if ("Stopped".equalsIgnoreCase(state)) {
            camelContext.getRouteController().stopRoute(routeId);
        } else if ("Suspended".equalsIgnoreCase(state)) {
            camelContext.getRouteController().suspendRoute(routeId);
        } else if ("Started".equalsIgnoreCase(state)) {
            camelContext.getRouteController().startRoute(routeId);
        }
    }

    /** Get status of a single route. */
    public String getRouteStatus(String routeId) {
        Route route = camelContext.getRoute(routeId);
        if (route == null) return "NOT_FOUND";
        try {
            return camelContext.getRouteController().getRouteStatus(routeId).name();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /** Expose dynamic REST services and their child/regular routes grouped by serviceId. */
    public List<Map<String, Object>> getServicesWithDetails() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            var onlineNodes = getOnlineNodes();
            var statesByRouteAndNode = getStatesByRouteAndNode();
            var allServices = getAllServices();
            var allLiveRoutes = listRoutes();

            for (var service : allServices) {
                String serviceId = service.getId();
                String prefix = "svc_" + serviceId.replaceAll("[^A-Za-z0-9_-]", "_") + "__";

                var serviceRoutes = filterRoutesForService(service, prefix, allLiveRoutes);
                var matchedRestRouteIds = new HashSet<String>();

                var serviceRests =
                        extractServiceRests(
                                service,
                                prefix,
                                serviceRoutes,
                                matchedRestRouteIds,
                                onlineNodes,
                                statesByRouteAndNode);
                var regularRoutes = extractRegularRoutes(serviceRoutes, matchedRestRouteIds);

                var serviceDetails = new LinkedHashMap<String, Object>();
                serviceDetails.put("serviceId", serviceId);
                serviceDetails.put("name", service.getName());
                serviceDetails.put(
                        "description",
                        service.getDescription() != null ? service.getDescription() : "");
                serviceDetails.put("rests", serviceRests);
                serviceDetails.put("routes", regularRoutes);

                result.add(serviceDetails);
            }
        } catch (Exception e) {
            log.error("Failed to extract service details from CamelContext", e);
        }
        return result;
    }

    private List<ClusterNodeEntity> getOnlineNodes() {
        return clusterNodeService.getAllNodes().stream()
                .map(
                        nodeMap -> {
                            ClusterNodeEntity entity = new ClusterNodeEntity();
                            entity.setInstanceId((String) nodeMap.get("instanceId"));
                            entity.setGroupName((String) nodeMap.get("groupName"));
                            entity.setIpAddress((String) nodeMap.get("ipAddress"));
                            Object portObj = nodeMap.get("port");
                            entity.setPort(
                                    portObj instanceof Number
                                            ? ((Number) portObj).intValue()
                                            : 8080);
                            entity.setStatus((String) nodeMap.get("status"));
                            Object startedObj = nodeMap.get("startedAt");
                            if (startedObj instanceof Instant) {
                                entity.setStartedAt((Instant) startedObj);
                            } else if (startedObj instanceof String) {
                                entity.setStartedAt(Instant.parse((String) startedObj));
                            }
                            Object seenObj = nodeMap.get("lastSeen");
                            if (seenObj instanceof Instant) {
                                entity.setLastSeen((Instant) seenObj);
                            } else if (seenObj instanceof String) {
                                entity.setLastSeen(Instant.parse((String) seenObj));
                            }
                            return entity;
                        })
                .filter(n -> NodeState.ONLINE.getValue().equalsIgnoreCase(n.getStatus()))
                .collect(Collectors.toList());
    }

    private Map<String, Map<String, RouteRuntimeStateEntity>> getStatesByRouteAndNode() {
        return clusterNodeService.getAllRouteStates().stream()
                .collect(
                        Collectors.groupingBy(
                                RouteRuntimeStateEntity::getRouteId,
                                Collectors.toMap(
                                        RouteRuntimeStateEntity::getInstanceId,
                                        state -> state,
                                        (s1, s2) -> s1)));
    }

    private List<ServiceDTO> getAllServices() {
        return serviceRepository.findAll().stream()
                .map(
                        entity -> {
                            var s = new ServiceDTO();
                            s.setId(entity.getId().toString());
                            s.setName(entity.getName());
                            s.setDescription(entity.getDescription());
                            s.setCreatedAt(entity.getCreatedAt());
                            s.setUpdatedAt(entity.getUpdatedAt());
                            return s;
                        })
                .collect(Collectors.toList());
    }

    private List<RouteInfo> filterRoutesForService(
            ServiceDTO service, String prefix, List<RouteInfo> allLiveRoutes) {
        String serviceId = service.getId();
        return allLiveRoutes.stream()
                .filter(
                        r ->
                                serviceId.equals(r.serviceId())
                                        || (r.id() != null && r.id().startsWith(prefix))
                                        || (service.getRouteIds() != null
                                                && service.getRouteIds().contains(r.id())))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> extractServiceRests(
            ServiceDTO service,
            String prefix,
            List<RouteInfo> serviceRoutes,
            Set<String> matchedRestRouteIds,
            List<ClusterNodeEntity> onlineNodes,
            Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode) {

        List<Map<String, Object>> serviceRests = new ArrayList<>();
        serviceRests.addAll(
                extractRestsFromDsl(
                        service,
                        prefix,
                        serviceRoutes,
                        matchedRestRouteIds,
                        onlineNodes,
                        statesByRouteAndNode));
        serviceRests.addAll(
                extractStandaloneRestRoutes(
                        serviceRoutes, matchedRestRouteIds, onlineNodes, statesByRouteAndNode));
        return serviceRests;
    }

    private List<Map<String, Object>> extractRestsFromDsl(
            ServiceDTO service,
            String prefix,
            List<RouteInfo> serviceRoutes,
            Set<String> matchedRestRouteIds,
            List<ClusterNodeEntity> onlineNodes,
            Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode) {

        List<Map<String, Object>> dslRests = new ArrayList<>();
        if (camelContext instanceof ModelCamelContext modelContext) {
            List<RestDefinition> restDefinitions = modelContext.getRestDefinitions();
            if (restDefinitions != null) {
                for (RestDefinition rest : restDefinitions) {
                    if (rest.getVerbs() == null) {
                        continue;
                    }
                    List<Map<String, Object>> verbList = new ArrayList<>();
                    boolean restBelongsToService = false;

                    for (VerbDefinition verb : rest.getVerbs()) {
                        if (verbBelongsToService(verb, service, prefix, serviceRoutes, rest)) {
                            restBelongsToService = true;
                            String actualRouteId = resolveActualRouteId(verb, serviceRoutes, rest);
                            updateMatchedRestRouteIds(
                                    actualRouteId,
                                    verb.getRouteId(),
                                    serviceRoutes,
                                    matchedRestRouteIds);
                            verbList.add(
                                    buildVerbDetails(
                                            verb,
                                            actualRouteId,
                                            onlineNodes,
                                            statesByRouteAndNode));
                        }
                    }

                    if (restBelongsToService) {
                        dslRests.add(
                                buildRestDetails(
                                        rest, verbList, onlineNodes, statesByRouteAndNode));
                    }
                }
            }
        }
        return dslRests;
    }

    private List<Map<String, Object>> extractStandaloneRestRoutes(
            List<RouteInfo> serviceRoutes,
            Set<String> matchedRestRouteIds,
            List<ClusterNodeEntity> onlineNodes,
            Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode) {

        List<Map<String, Object>> standaloneRests = new ArrayList<>();
        for (RouteInfo ri : serviceRoutes) {
            String sourceUri = ri.sourceUri();
            if (sourceUri == null) {
                continue;
            }
            String src = sourceUri.toLowerCase().trim();
            if (src.startsWith("rest:")
                    || src.startsWith("rest://")
                    || src.startsWith("rest-api:")
                    || src.startsWith("rest-api://")
                    || src.startsWith("rest-openapi:")
                    || src.startsWith("rest-openapi://")) {
                if (!matchedRestRouteIds.contains(ri.id())
                        && (ri.originalId() == null
                                || !matchedRestRouteIds.contains(ri.originalId()))) {
                    ParsedRestUri parsed = parseRestUri(sourceUri, restParamExtractor);
                    if (parsed != null) {
                        matchedRestRouteIds.add(ri.id());
                        if (ri.originalId() != null) {
                            matchedRestRouteIds.add(ri.originalId());
                        }

                        Map<String, Object> verbDetails = buildStandaloneVerbMap(ri, parsed);
                        List<Map<String, Object>> verbList = new ArrayList<>();
                        verbList.add(verbDetails);

                        Map<String, Object> restDetails = new LinkedHashMap<>();
                        restDetails.put("path", parsed.path);
                        restDetails.put(
                                "description", ri.description() != null ? ri.description() : "");
                        restDetails.put("status", ri.status() != null ? ri.status() : "UNKNOWN");
                        restDetails.put("routeId", ri.id());
                        restDetails.put(
                                "nodeStates",
                                ri.nodeStates() != null ? ri.nodeStates() : new ArrayList<>());
                        restDetails.put("verbs", verbList);

                        standaloneRests.add(restDetails);
                    }
                }
            }
        }
        return standaloneRests;
    }

    private boolean verbBelongsToService(
            VerbDefinition verb,
            ServiceDTO service,
            String prefix,
            List<RouteInfo> serviceRoutes,
            RestDefinition rest) {
        String verbRouteId = verb.getRouteId();
        if (verbRouteId != null && !verbRouteId.isEmpty()) {
            if (verbRouteId.startsWith(prefix)) {
                return true;
            }
            List<String> serviceRouteIds = service.getRouteIds();
            if (serviceRouteIds != null) {
                if (serviceRouteIds.contains(verbRouteId)) {
                    return true;
                }
                for (String rId : serviceRouteIds) {
                    if (rId.endsWith("__" + verbRouteId)) {
                        return true;
                    }
                }
            }
        }
        for (RouteInfo ri : serviceRoutes) {
            if (isRouteMatchingVerb(ri, verb, rest)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRouteMatchingVerb(RouteInfo ri, VerbDefinition verb) {
        String verbRouteId = verb.getRouteId();
        if (verbRouteId != null && !verbRouteId.isEmpty()) {
            return ri.id().equals(verbRouteId)
                    || ri.originalId().equals(verbRouteId)
                    || ri.id().endsWith("__" + verbRouteId);
        }
        return false;
    }

    private boolean isRouteMatchingVerb(RouteInfo ri, VerbDefinition verb, RestDefinition rest) {
        if (isRouteMatchingVerb(ri, verb)) {
            return true;
        }
        String sourceUri = ri.sourceUri();
        if (sourceUri == null) {
            return false;
        }
        String verbMethod = verb.getClass().getSimpleName().replace("Definition", "").toLowerCase();
        String restBasePath = rest.getPath() != null ? rest.getPath() : "";
        String verbSubPath = verb.getPath() != null ? verb.getPath() : "";

        String combinedPath =
                ("/" + restBasePath + "/" + verbSubPath)
                        .replace('\\', '/')
                        .replaceAll("/+", "/")
                        .replaceAll("/+$", "");

        if (combinedPath.isEmpty()) {
            combinedPath = "/";
        }

        ParsedRestUri parsed = parseRestUri(sourceUri, restParamExtractor);
        if (parsed == null) {
            return false;
        }
        if (!parsed.method.equalsIgnoreCase(verbMethod)) {
            return false;
        }
        String normRoutePath = restParamExtractor.normalizeRestPathForComparison(parsed.path);
        String normVerbPath = restParamExtractor.normalizeRestPathForComparison(combinedPath);
        return normRoutePath.equals(normVerbPath);
    }

    private String resolveActualRouteId(
            VerbDefinition verb, List<RouteInfo> serviceRoutes, RestDefinition rest) {
        for (RouteInfo ri : serviceRoutes) {
            if (isRouteMatchingVerb(ri, verb, rest)) {
                return ri.id();
            }
        }
        return null;
    }

    private Map<String, Object> buildStandaloneVerbMap(RouteInfo ri, ParsedRestUri parsed) {
        Map<String, Object> verbDetails = new LinkedHashMap<>();
        verbDetails.put("method", parsed.method);
        verbDetails.put("id", ri.originalId() != null ? ri.originalId() : "");
        verbDetails.put("routeId", ri.id());
        verbDetails.put("status", ri.status() != null ? ri.status() : "UNKNOWN");
        verbDetails.put("description", ri.description() != null ? ri.description() : "");
        verbDetails.put("consumes", parsed.consumes != null ? parsed.consumes : "");
        verbDetails.put("produces", parsed.produces != null ? parsed.produces : "");
        verbDetails.put("toUri", "");

        List<Map<String, Object>> paramList =
                Optional.ofNullable(ri.restParams()).orElse(Collections.emptyList()).stream()
                        .map(
                                param -> {
                                    Map<String, Object> paramDetails = new LinkedHashMap<>();
                                    paramDetails.put("name", Objects.toString(param.name(), ""));
                                    paramDetails.put(
                                            "type", Objects.toString(param.type(), "query"));
                                    paramDetails.put(
                                            "dataType",
                                            Objects.toString(param.dataType(), "string"));
                                    paramDetails.put("required", param.required());
                                    paramDetails.put(
                                            "description",
                                            Objects.toString(param.description(), ""));
                                    return paramDetails;
                                })
                        .toList();
        verbDetails.put("params", paramList);
        verbDetails.put(
                "nodeStates", ri.nodeStates() != null ? ri.nodeStates() : new ArrayList<>());
        return verbDetails;
    }

    private void updateMatchedRestRouteIds(
            String actualRouteId,
            String verbRouteId,
            List<RouteInfo> serviceRoutes,
            Set<String> matchedRestRouteIds) {
        if (actualRouteId != null && !actualRouteId.isEmpty()) {
            matchedRestRouteIds.add(actualRouteId);
            for (var ri : serviceRoutes) {
                if (ri.id().endsWith("__" + actualRouteId)
                        || (verbRouteId != null && ri.id().endsWith("__" + verbRouteId))) {
                    matchedRestRouteIds.add(ri.id());
                }
            }
        }
    }

    private static class ParsedRestUri {
        final String method;
        final String path;
        final String consumes;
        final String produces;

        ParsedRestUri(String method, String path, String consumes, String produces) {
            this.method = method;
            this.path = path;
            this.consumes = consumes;
            this.produces = produces;
        }
    }

    private static ParsedRestUri parseRestUri(String uri, RestParamExtractor restParamExtractor) {
        RestParamExtractor.ParsedRestUri p = restParamExtractor.parseRestUri(uri);
        if (p == null) {
            return null;
        }
        return new ParsedRestUri(p.method, p.path, p.consumes, p.produces);
    }

    private Map<String, Object> buildVerbDetails(
            VerbDefinition verb,
            String actualRouteId,
            List<ClusterNodeEntity> onlineNodes,
            Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode) {

        String method = verb.getClass().getSimpleName().replace("Definition", "").toUpperCase();
        boolean hasRoute = actualRouteId != null && !actualRouteId.isEmpty();

        var verbDetails = new LinkedHashMap<String, Object>();
        verbDetails.put("method", method);
        verbDetails.put("id", Objects.toString(verb.getId(), ""));
        verbDetails.put("routeId", Objects.toString(actualRouteId, ""));
        verbDetails.put("status", hasRoute ? getRouteStatus(actualRouteId) : "UNKNOWN");
        verbDetails.put("description", Objects.toString(verb.getDescriptionText(), ""));
        verbDetails.put("consumes", Objects.toString(verb.getConsumes(), ""));
        verbDetails.put("produces", Objects.toString(verb.getProduces(), ""));
        verbDetails.put("toUri", Optional.ofNullable(verb.getTo()).map(t -> t.getUri()).orElse(""));

        var paramList =
                Optional.ofNullable(verb.getParams()).orElse(Collections.emptyList()).stream()
                        .map(
                                param -> {
                                    var paramDetails = new LinkedHashMap<String, Object>();
                                    paramDetails.put("name", Objects.toString(param.getName(), ""));
                                    paramDetails.put(
                                            "type",
                                            Optional.ofNullable(param.getType())
                                                    .map(t -> t.name().toLowerCase())
                                                    .orElse("query"));
                                    paramDetails.put(
                                            "dataType",
                                            Objects.toString(param.getDataType(), "string"));
                                    paramDetails.put(
                                            "required",
                                            Optional.ofNullable(param.getRequired()).orElse(false));
                                    paramDetails.put(
                                            "description",
                                            Objects.toString(param.getDescription(), ""));
                                    return paramDetails;
                                })
                        .toList();

        verbDetails.put("params", paramList);
        verbDetails.put(
                "nodeStates",
                routeNodeStateProvider.getNodeStatesForRoute(
                        actualRouteId, onlineNodes, statesByRouteAndNode));
        return verbDetails;
    }

    private Map<String, Object> buildRestDetails(
            RestDefinition rest,
            List<Map<String, Object>> verbList,
            List<ClusterNodeEntity> onlineNodes,
            Map<String, Map<String, RouteRuntimeStateEntity>> statesByRouteAndNode) {

        String basePath = rest.getPath() != null ? rest.getPath() : "";
        String normalizedPath =
                ("/" + basePath).replace('\\', '/').replaceAll("/+", "/").replaceAll("/+$", "");
        if (normalizedPath.isEmpty()) {
            normalizedPath = "/";
        }

        var restDetails = new LinkedHashMap<String, Object>();
        restDetails.put("path", normalizedPath);
        restDetails.put(
                "description", rest.getDescriptionText() != null ? rest.getDescriptionText() : "");

        String restStatus = "UNKNOWN";
        String restRouteId = "";
        if (!verbList.isEmpty()) {
            restRouteId = (String) verbList.get(0).get("routeId");
            if (restRouteId != null && !restRouteId.isEmpty()) {
                restStatus = getRouteStatus(restRouteId);
            }
        }
        restDetails.put("status", restStatus);
        restDetails.put("routeId", restRouteId != null ? restRouteId : "");
        restDetails.put(
                "nodeStates",
                routeNodeStateProvider.getNodeStatesForRoute(
                        restRouteId, onlineNodes, statesByRouteAndNode));
        restDetails.put("verbs", verbList);
        return restDetails;
    }

    private List<Map<String, Object>> extractRegularRoutes(
            List<RouteInfo> serviceRoutes, Set<String> matchedRestRouteIds) {
        List<Map<String, Object>> regularRoutes = new ArrayList<>();
        for (var ri : serviceRoutes) {
            boolean isRestRoute =
                    matchedRestRouteIds.contains(ri.id())
                            || matchedRestRouteIds.contains(ri.originalId());
            if (!isRestRoute && ri.sourceUri() != null) {
                String src = ri.sourceUri().toLowerCase().trim();
                if (src.startsWith("rest:") || src.startsWith("rest://")) {
                    isRestRoute = true;
                }
            }

            if (!isRestRoute) {
                var routeDetails = new LinkedHashMap<String, Object>();
                routeDetails.put("id", ri.id());
                routeDetails.put("originalId", ri.originalId());
                routeDetails.put("status", ri.status());
                routeDetails.put("description", ri.description() != null ? ri.description() : "");
                routeDetails.put("sourceUri", ri.sourceUri());
                routeDetails.put("activeVersion", ri.activeVersion());
                routeDetails.put("totalVersions", ri.totalVersions());
                routeDetails.put("persistent", ri.persistent());
                routeDetails.put("createdAt", ri.createdAt());
                routeDetails.put("updatedAt", ri.updatedAt());
                routeDetails.put("deployedAt", ri.deployedAt());
                routeDetails.put("nodeStates", ri.nodeStates());
                regularRoutes.add(routeDetails);
            }
        }
        return regularRoutes;
    }

    @Transactional
    public void cleanUpRestDefinitions(String serviceId, Collection<String> routeIds) {
        routeRestCleanupService.cleanUpRestDefinitions(serviceId, routeIds);
    }

    @Transactional
    public void cleanUpRestDefinitions(String routeId) {
        routeRestCleanupService.cleanUpRestDefinitions(routeId);
    }

    /** Start a route by ID. */
    @Transactional
    public void startRoute(String routeId) throws Exception {
        camelContext.getRouteController().startRoute(routeId);
        routeStateService.saveDesiredState(routeId, RouteState.STARTED.getValue());
        log.info("Route {} started", routeId);
        auditLogger.logRouteAction(routeId, "Started");
    }

    /** Stop a route by ID. */
    @Transactional
    public void stopRoute(String routeId) throws Exception {
        camelContext.getRouteController().stopRoute(routeId);
        routeStateService.saveDesiredState(routeId, RouteState.STOPPED.getValue());
        log.info("Route {} stopped", routeId);
        auditLogger.logRouteAction(routeId, "Stopped");
    }

    /** Suspend a route by ID. */
    @Transactional
    public void suspendRoute(String routeId) throws Exception {
        camelContext.getRouteController().suspendRoute(routeId);
        routeStateService.saveDesiredState(routeId, RouteState.SUSPENDED.getValue());
        log.info("Route {} suspended", routeId);
        auditLogger.logRouteAction(routeId, "Suspended");
    }

    /** Resume a suspended route by ID. */
    @Transactional
    public void resumeRoute(String routeId) throws Exception {
        camelContext.getRouteController().resumeRoute(routeId);
        routeStateService.saveDesiredState(routeId, RouteState.STARTED.getValue());
        log.info("Route {} resumed", routeId);
        auditLogger.logRouteAction(routeId, "Resumed");
    }

    /** Remove a route from Camel context. */
    @Transactional
    public boolean removeRoute(String routeId) throws Exception {
        Route route = camelContext.getRoute(routeId);
        boolean existed = (route != null);
        if (route != null) {
            org.apache.camel.ServiceStatus status =
                    camelContext.getRouteController().getRouteStatus(routeId);

            if (status != null && !status.isStopped()) {
                throw new IllegalStateException(
                        "Route "
                                + routeId
                                + " is currently "
                                + status
                                + " and must be stopped before deletion.");
            }
        }
        // Clean up REST definitions associated with this route ID
        routeRestCleanupService.cleanUpRestDefinitions(routeId);
        routeEndpointCleanupService.cleanUpEndpointsForRoute(routeId);

        boolean removed = true;
        if (existed) {
            removed = camelContext.removeRoute(routeId) || camelContext.getRoute(routeId) == null;
        }

        if (removed) {
            routeStateService.removeDesiredState(routeId);

            try {
                List<RouteVersionEntity> activeVersions = routeVersionRepository.findAll();
                for (RouteVersionEntity rve : activeVersions) {
                    if (Boolean.TRUE.equals(rve.getAutoRestore()) && rve.getRouteIds() != null) {
                        List<String> routeIdsList = Arrays.asList(rve.getRouteIds().split(","));
                        if (routeIdsList.contains(routeId)) {
                            log.info(
                                    "Disabling autoRestore for version {} because route {} is being"
                                            + " deleted",
                                    rve.getId(),
                                    routeId);
                            rve.setAutoRestore(false);
                            routeVersionRepository.save(rve);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to disable auto-restore for version containing deleted route '{}':"
                                + " {}",
                        routeId,
                        e.getMessage());
            }

            auditLogger.logRouteAction(routeId, "Deleted/Removed");
        }
        log.info("Route {} removal result: {}", routeId, removed);
        return removed;
    }

    /**
     * Deploy a route from a specific version. Reads YAML directly from disk to avoid
     * JSON-serialization corruption. If a route with the same ID already exists, it will be
     * replaced.
     */
    @Transactional
    public String deployFromVersion(String versionId) throws Exception {
        return routeDeploymentFacade.deployFromVersion(versionId);
    }
}
