package vn.cxn.apache_camel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.config.RedisClusterProperties;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.entity.RouteEntity;
import vn.cxn.apache_camel.model.entity.RouteVersionEntity;
import vn.cxn.apache_camel.model.entity.ServiceEntity;
import vn.cxn.apache_camel.repository.RouteRepository;
import vn.cxn.apache_camel.repository.RouteVersionRepository;
import vn.cxn.apache_camel.repository.ServiceRepository;
import vn.cxn.apache_camel.service.mapper.RouteVersionMapper;
import vn.cxn.apache_camel.service.route_document.RouteDocumentStrategy;
import vn.cxn.apache_camel.util.CamelRouteUtil;

@Service
@Transactional(readOnly = true)
public class RouteVersionService {

    private static final Logger log = LoggerFactory.getLogger(RouteVersionService.class);

    private final RouteVersionRepository versionRepository;
    private final ServiceRepository serviceRepository;
    private final RouteRepository routeRepository;
    private final org.apache.camel.CamelContext camelContext;
    private final RouteStateService routeStateService;
    private final ClusterNodeService clusterNodeService;
    private final org.springframework.beans.factory.ObjectProvider<RedisTemplate<String, Object>>
            redisTemplateProvider;
    private final List<RouteDocumentStrategy> routeDocumentStrategies;
    private final RouteVersionMapper routeVersionMapper;

    private final RedisClusterProperties properties;

    @Value("${camel.dashboard.cluster.stream-key:cluster:event:stream}")
    private String streamKey;

    @Value("${camel.dashboard.cluster.channel:cluster:event:channel}")
    private String channel;

    public RouteVersionService(
            RouteVersionRepository versionRepository,
            ServiceRepository serviceRepository,
            RouteRepository routeRepository,
            @org.springframework.context.annotation.Lazy org.apache.camel.CamelContext camelContext,
            RouteStateService routeStateService,
            ClusterNodeService clusterNodeService,
            org.springframework.beans.factory.ObjectProvider<RedisTemplate<String, Object>>
                    redisTemplateProvider,
            List<RouteDocumentStrategy> routeDocumentStrategies,
            RouteVersionMapper routeVersionMapper,
            RedisClusterProperties properties) {
        this.versionRepository = versionRepository;
        this.serviceRepository = serviceRepository;
        this.routeRepository = routeRepository;
        this.camelContext = camelContext;
        this.routeStateService = routeStateService;
        this.clusterNodeService = clusterNodeService;
        this.redisTemplateProvider = redisTemplateProvider;
        this.routeDocumentStrategies = routeDocumentStrategies;
        this.routeVersionMapper = routeVersionMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        log.info("RouteVersionService initialized with PostgreSQL backend.");
        migrateExistingRecords();
    }

    private void migrateExistingRecords() {
        try {
            List<RouteVersionEntity> all = versionRepository.findAll();
            for (RouteVersionEntity entity : all) {
                healRouteVersion(entity);
            }
            log.info("Completed metadata migration and healing for existing route versions.");
        } catch (Exception e) {
            log.error("Failed to migrate existing route version records", e);
        }
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.asList(value.split(","));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeDescriptions(String value) {
        if (value == null || value.isBlank()) {
            return new HashMap<>();
        }
        try {
            return new ObjectMapper().readValue(value, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Transactional
    public void healRouteVersion(RouteVersionEntity entity) {
        if (entity == null
                || entity.getYamlContent() == null
                || entity.getYamlContent().isBlank()) {
            return;
        }
        try {
            String originalContent = entity.getYamlContent();
            String fileName = entity.getFileName();
            String serviceId = entity.getService().getId().toString();

            String ensuredContent =
                    getRouteDocumentStrategy(fileName).ensureRouteIds(originalContent);

            boolean needsMetadataUpdate =
                    entity.getRouteIds() == null || entity.getOriginalRouteIds() == null;
            boolean contentChanged = !Objects.equals(originalContent, ensuredContent);

            if (contentChanged || needsMetadataUpdate) {
                log.info(
                        "Healing/migrating route version {} (version {}) for service {}",
                        entity.getId(),
                        entity.getVersion(),
                        serviceId);

                entity.setYamlContent(ensuredContent);
                List<String> originalIds = extractRouteIds(fileName, ensuredContent);
                List<String> managedIds = toManagedRouteIds(serviceId, originalIds);
                Map<String, String> descriptions =
                        extractRouteDescriptions(fileName, ensuredContent, serviceId);

                entity.setOriginalRouteIds(String.join(",", originalIds));
                entity.setRouteIds(String.join(",", managedIds));
                try {
                    entity.setRouteDescriptions(
                            new ObjectMapper().writeValueAsString(descriptions));
                } catch (Exception e) {
                    log.warn(
                            "Failed to serialize route descriptions during healing: {}",
                            e.getMessage());
                }

                versionRepository.save(entity);

                // Disk cache update removed: everything is in the DB now
            }
        } catch (Exception e) {
            log.warn("Failed to heal route version {}: {}", entity.getId(), e.getMessage());
        }
    }

    @Transactional
    public RouteVersion uploadRoute(
            String serviceId, String fileName, String content, String description) {
        return uploadRoute(serviceId, fileName, content, description, null);
    }

    @Transactional
    public RouteVersion uploadRoute(
            String serviceId,
            String fileName,
            String content,
            String description,
            List<vn.cxn.apache_camel.validation.ValidationWarning> warnings) {
        try {
            content = getRouteDocumentStrategy(fileName).ensureRouteIds(content);
        } catch (Exception e) {
            log.warn("Failed to ensure route IDs in uploadRoute: {}", e.getMessage());
        }
        List<String> discoveredIds = extractRouteIds(fileName, content);
        List<String> managedIds = toManagedRouteIds(serviceId, discoveredIds);

        int nextVersion = getNextVersionForService(serviceId);
        UUID versionId = UUID.randomUUID();

        Optional<ServiceEntity> sOpt = serviceRepository.findById(parseUuid(serviceId));
        if (sOpt.isEmpty()) {
            throw new IllegalArgumentException("Service not found with ID: " + serviceId);
        }

        RouteVersionEntity rve = new RouteVersionEntity();
        rve.setId(versionId);
        rve.setService(sOpt.get());
        rve.setFileName(fileName);
        rve.setYamlContent(content);
        rve.setVersion(nextVersion);
        rve.setDescription(description);
        rve.setAutoRestore(false);
        rve.setUploadedAt(Instant.now());
        rve.setUpdatedAt(Instant.now());
        rve.setUpdatedBy("");

        // Populate new metadata columns
        rve.setOriginalRouteIds(String.join(",", discoveredIds));
        rve.setRouteIds(String.join(",", managedIds));
        try {
            rve.setRouteDescriptions(
                    new ObjectMapper()
                            .writeValueAsString(
                                    extractRouteDescriptions(fileName, content, serviceId)));
        } catch (Exception e) {
            log.warn("Failed to serialize route descriptions during upload", e);
        }

        if (warnings != null && !warnings.isEmpty()) {
            try {
                rve.setValidateResult(new ObjectMapper().writeValueAsString(warnings));
            } catch (Exception e) {
                log.warn("Failed to serialize warnings during upload", e);
            }
        }

        RouteVersionEntity saved = versionRepository.save(rve);

        // Disk cache update removed: everything is in the DB now

        RouteVersion rv = routeVersionMapper.toModel(saved);
        rv.setOriginalRouteIds(discoveredIds);
        rv.setRouteIds(managedIds);
        rv.setRouteDescriptions(extractRouteDescriptions(fileName, content, serviceId));
        return rv;
    }

    public List<RouteVersion> getAllVersions() {
        return versionRepository.findAllSummary().stream()
                .map(routeVersionMapper::toModel)
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getVersionCountsByServiceId() {
        List<Object[]> results = versionRepository.countVersionsByServiceId();
        Map<String, Integer> counts = new HashMap<>();
        for (Object[] row : results) {
            if (row[0] != null && row[1] != null) {
                counts.put(row[0].toString(), ((Number) row[1]).intValue());
            }
        }
        return counts;
    }

    public List<RouteVersion> getVersionsByServiceId(String serviceId) {
        try {
            return versionRepository.findSummaryByServiceId(parseUuid(serviceId)).stream()
                    .map(routeVersionMapper::toModel)
                    .sorted(Comparator.comparingInt(RouteVersion::getVersion))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get versions by service ID: {}", serviceId, e);
            return new ArrayList<>();
        }
    }

    public List<RouteVersion> getVersionsByRouteId(String routeId) {
        return versionRepository.findAllSummary().stream()
                .map(routeVersionMapper::toModel)
                .filter(v -> v.getRouteIds() != null && v.getRouteIds().contains(routeId))
                .sorted(Comparator.comparingInt(RouteVersion::getVersion))
                .collect(Collectors.toList());
    }

    public Optional<RouteVersion> getActiveVersionByRouteId(String routeId) {
        try {
            Optional<RouteEntity> routeEntityOpt = routeRepository.findById(routeId);
            if (routeEntityOpt.isPresent() && routeEntityOpt.get().getVersion() != null) {
                RouteVersionEntity versionEntity = routeEntityOpt.get().getVersion();
                return Optional.of(routeVersionMapper.toModel(versionEntity));
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to find RouteEntity by routeId '{}' in DB: {}",
                    routeId,
                    e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<RouteVersion> getVersionById(String versionId) {
        try {
            return versionRepository
                    .findById(parseUuid(versionId))
                    .map(routeVersionMapper::toModel);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void markAsAutoRestore(String versionId) {
        updateAutoRestoreStatus(versionId, true);
    }

    @Transactional
    public boolean updateAutoRestoreStatus(String versionId, boolean autoRestore) {
        try {
            UUID vUuid = parseUuid(versionId);
            Optional<RouteVersionEntity> targetOpt = versionRepository.findById(vUuid);
            if (targetOpt.isEmpty()) return false;

            RouteVersionEntity target = targetOpt.get();
            if (autoRestore) {
                List<RouteVersionEntity> siblings =
                        versionRepository.findByServiceId(target.getService().getId());
                siblings.forEach(
                        v -> {
                            v.setAutoRestore(false);
                            versionRepository.save(v);
                        });
                target.setAutoRestore(true);
                target.setUploadedAt(Instant.now());
            } else {
                target.setAutoRestore(false);
            }
            versionRepository.save(target);
            return true;
        } catch (Exception e) {
            log.error("Failed to update auto-restore status: {}", versionId, e);
            return false;
        }
    }

    @Transactional
    public void updateDeployedAt(String versionId, Instant deployedAt) {
        try {
            UUID vUuid = parseUuid(versionId);
            Optional<RouteVersionEntity> targetOpt = versionRepository.findById(vUuid);
            if (targetOpt.isPresent()) {
                RouteVersionEntity target = targetOpt.get();
                target.setDeployedAt(deployedAt);
                versionRepository.save(target);
            }
        } catch (Exception e) {
            log.error("Failed to update deployedAt for version: {}", versionId, e);
        }
    }

    @Transactional
    public boolean deleteVersion(String versionId) {
        try {
            UUID vUuid = parseUuid(versionId);
            Optional<RouteVersionEntity> targetOpt = versionRepository.findById(vUuid);
            if (targetOpt.isEmpty()) return false;

            RouteVersionEntity rv = targetOpt.get();
            RouteVersion model = routeVersionMapper.toModel(rv);

            // Capture metadata BEFORE deletion so the event payload is self-contained
            String routeIdsSnapshot =
                    model.getRouteIds() != null ? String.join(",", model.getRouteIds()) : "";
            String fileNameSnapshot = versionId + ".yaml";

            if (model.getRouteIds() != null) {
                for (String routeId : model.getRouteIds()) {
                    try {
                        if (camelContext.getRoute(routeId) != null) {
                            camelContext.getRouteController().stopRoute(routeId);
                            camelContext.removeRoute(routeId);
                            routeStateService.removeDesiredState(routeId);
                            log.info(
                                    "Successfully removed route '{}' from CamelContext during"
                                            + " version '{}' deletion",
                                    routeId,
                                    versionId);
                        }
                    } catch (Exception e) {
                        log.warn(
                                "Failed to remove route '{}' from CamelContext during version '{}'"
                                        + " deletion: {}",
                                routeId,
                                versionId,
                                e.getMessage());
                    }
                }
            }

            // Disk cache deletion removed: everything is in the DB now

            routeStateService.deleteByVersionId(vUuid);
            versionRepository.delete(rv);

            // Broadcast DELETE_VERSION event to cluster peers after successful local
            // deletion
            if (clusterNodeService.isRedisEnabled()) {
                publishDeleteVersionEvent(versionId, routeIdsSnapshot, fileNameSnapshot);
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to delete version: {}", versionId, e);
            return false;
        }
    }

    /**
     * Broadcasts a self-contained DELETE_VERSION event to all cluster peers via Redis Streams +
     * Pub/Sub. The routeIds and fileName are embedded in the event payload so peers do not need to
     * query the database (the record is already deleted by the time they receive the event).
     */
    private void publishDeleteVersionEvent(
            String versionId, String routeIdsSnapshot, String fileNameSnapshot) {
        try {
            RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                log.warn(
                        "RouteVersionService: RedisTemplate not available; cannot broadcast"
                                + " DELETE_VERSION event.");
                return;
            }

            String instanceId = clusterNodeService.getInstanceId();

            Map<String, String> eventMap = new LinkedHashMap<>();
            eventMap.put("eventType", "DELETE_VERSION");
            eventMap.put("targetId", versionId);
            eventMap.put("initiatorId", instanceId);
            eventMap.put("timestamp", String.valueOf(System.currentTimeMillis()));
            eventMap.put("meta_routeIds", routeIdsSnapshot);
            eventMap.put("meta_fileName", fileNameSnapshot);

            RecordId recordId =
                    redisTemplate
                            .opsForStream()
                            .add(MapRecord.create(streamKey, eventMap), XAddOptions.maxlen(1000L));

            if (recordId != null) {
                String eventId = recordId.getValue();
                log.info(
                        "RouteVersionService: Broadcasted DELETE_VERSION event for version '{}'"
                                + " (stream ID: {})",
                        versionId,
                        eventId);

                // Alert other nodes via Pub/Sub
                redisTemplate.convertAndSend(channel, eventId);

                // Update this node's own checkpoint so it doesn't re-execute this event on
                // recovery
                redisTemplate.opsForValue().set(properties.checkpointKey(instanceId), eventId);
            }
        } catch (Exception e) {
            log.error(
                    "RouteVersionService: Failed to broadcast DELETE_VERSION event for version"
                            + " '{}': {}",
                    versionId,
                    e.getMessage(),
                    e);
        }
    }

    public List<String> getAllRouteIds() {
        return versionRepository.findAllSummary().stream()
                .map(routeVersionMapper::toModel)
                .filter(v -> v.getRouteIds() != null)
                .flatMap(v -> v.getRouteIds().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getRouteIdsForService(String serviceId) {
        try {
            return versionRepository.findSummaryByServiceId(parseUuid(serviceId)).stream()
                    .map(routeVersionMapper::toModel)
                    .filter(v -> v.getRouteIds() != null)
                    .flatMap(v -> v.getRouteIds().stream())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Transactional
    public void deleteVersionsByServiceId(String serviceId) {
        try {
            List<RouteVersionEntity> list = versionRepository.findByServiceId(parseUuid(serviceId));
            for (RouteVersionEntity v : list) {
                deleteVersion(v.getId().toString());
            }
        } catch (Exception e) {
            log.error("Failed to delete versions by service ID: {}", serviceId, e);
        }
    }

    public String getContentFromDisk(String versionId) throws IOException {
        return getContentDb(versionId);
    }

    public String getContentDb(String versionId) throws IOException {
        try {
            Optional<RouteVersionEntity> opt = versionRepository.findById(parseUuid(versionId));
            if (opt.isPresent() && opt.get().getYamlContent() != null) {
                return opt.get().getYamlContent();
            }
        } catch (Exception e) {
            throw new IOException("Failed to load content from DB for version: " + versionId, e);
        }
        throw new IOException("DB record not found for version: " + versionId);
    }

    @Transactional
    public String getDeploymentContent(RouteVersion version) throws IOException {
        Optional<RouteVersionEntity> opt = versionRepository.findById(parseUuid(version.getId()));
        if (opt.isPresent()) {
            RouteVersionEntity entity = opt.get();
            healRouteVersion(entity);

            // Sync the healed data back to the DTO version in-place
            version.setOriginalRouteIds(splitCsv(entity.getOriginalRouteIds()));
            version.setRouteIds(splitCsv(entity.getRouteIds()));
            version.setContent(entity.getYamlContent());
            version.setRouteDescriptions(deserializeDescriptions(entity.getRouteDescriptions()));
        }

        normalizeRouteMetadata(version);
        String content = getContentFromDisk(version.getId());
        String serviceId = version.getServiceId();
        String prefix =
                (serviceId != null && !serviceId.isBlank())
                        ? CamelRouteUtil.getServicePrefix(serviceId)
                        : "";
        return rewriteRouteIds(version.getFileName(), content, getRouteIdMapping(version), prefix);
    }

    public String getOriginalRouteId(RouteVersion version, String managedRouteId) {
        normalizeRouteMetadata(version);
        Map<String, String> reverse = new HashMap<>();
        Map<String, String> mapping = getRouteIdMapping(version);
        mapping.forEach((original, managed) -> reverse.put(managed, original));
        return reverse.getOrDefault(managedRouteId, managedRouteId);
    }

    public String getRouteDescription(RouteVersion version, String managedRouteId) {
        normalizeRouteMetadata(version);
        if (version.getRouteDescriptions() == null) {
            return null;
        }
        return version.getRouteDescriptions().get(managedRouteId);
    }

    public Set<String> getDeploymentSignatures(RouteVersion version) throws IOException {
        normalizeRouteMetadata(version);
        Set<String> signatures = new LinkedHashSet<>();
        if (version.getOriginalRouteIds() != null) {
            version.getOriginalRouteIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(id -> "route-id:" + id.trim())
                    .forEach(signatures::add);
        }
        signatures.addAll(
                extractEndpointSignatures(
                        version.getFileName(), getContentFromDisk(version.getId())));
        return signatures;
    }

    public Set<String> getDeploymentSignatures(String fileName, String content) {
        Set<String> signatures = new LinkedHashSet<>();
        List<String> originalIds = extractRouteIds(fileName, content);
        if (originalIds != null) {
            originalIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(id -> "route-id:" + id.trim())
                    .forEach(signatures::add);
        }
        signatures.addAll(extractEndpointSignatures(fileName, content));
        return signatures;
    }

    public String getApiContextPathSignature(String fileName, String content) {
        try {
            return getRouteDocumentStrategy(fileName).extractApiContextPathSignature(content);
        } catch (Exception e) {
            return null;
        }
    }

    private int getNextVersionForService(String serviceId) {
        try {
            return versionRepository.findByServiceId(parseUuid(serviceId)).stream()
                            .mapToInt(RouteVersionEntity::getVersion)
                            .max()
                            .orElse(0)
                    + 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private void normalizeRouteMetadata(RouteVersion version) {
        if (version == null) {
            return;
        }
        List<String> originalIds = version.getOriginalRouteIds();
        if (originalIds == null || originalIds.isEmpty()) {
            originalIds = extractRouteIds(version.getFileName(), version.getContent());
            version.setOriginalRouteIds(originalIds);
        }
        version.setRouteIds(toManagedRouteIds(version.getServiceId(), originalIds));
        if (version.getRouteDescriptions() == null) {
            version.setRouteDescriptions(new HashMap<>());
        }
        if (version.getRouteDescriptions().isEmpty() && version.getContent() != null) {
            version.setRouteDescriptions(
                    extractRouteDescriptions(
                            version.getFileName(), version.getContent(), version.getServiceId()));
        }
    }

    public List<String> toManagedRouteIds(String serviceId, List<String> originalIds) {
        if (originalIds == null) {
            return new ArrayList<>();
        }
        return originalIds.stream()
                .filter(Objects::nonNull)
                .map(id -> toManagedRouteId(serviceId, id))
                .distinct()
                .collect(Collectors.toList());
    }

    public String toManagedRouteId(String serviceId, String originalRouteId) {
        if (serviceId == null
                || serviceId.isBlank()
                || originalRouteId == null
                || originalRouteId.isBlank()) {
            return originalRouteId;
        }
        String prefix = CamelRouteUtil.getServicePrefix(serviceId);
        if (originalRouteId.startsWith(prefix)) {
            return originalRouteId;
        }
        return prefix + originalRouteId;
    }

    private Map<String, String> getRouteIdMapping(RouteVersion version) {
        List<String> originalIds =
                version.getOriginalRouteIds() != null
                        ? version.getOriginalRouteIds()
                        : new ArrayList<>();
        Map<String, String> mapping = new LinkedHashMap<>();
        originalIds.forEach(id -> mapping.put(id, toManagedRouteId(version.getServiceId(), id)));
        return mapping;
    }

    private String rewriteRouteIds(
            String fileName, String content, Map<String, String> routeIdMapping, String prefix)
            throws IOException {
        if (routeIdMapping.isEmpty() && (prefix == null || prefix.isEmpty())) {
            return content;
        }
        return getRouteDocumentStrategy(fileName).rewriteRouteIds(content, routeIdMapping, prefix);
    }

    public Set<String> extractEndpointSignatures(String fileName, String content) {
        Set<String> signatures = new LinkedHashSet<>();
        try {
            signatures.addAll(
                    getRouteDocumentStrategy(fileName).extractEndpointSignatures(content));
        } catch (Exception e) {
            log.warn("Could not extract endpoint signatures from content: {}", e.getMessage());
        }
        return signatures;
    }

    public List<String> extractRouteIds(String fileName, String content) {
        List<String> ids = new ArrayList<>();
        try {
            ids.addAll(getRouteDocumentStrategy(fileName).extractRouteIds(content));
        } catch (Exception e) {
            log.warn("Could not extract routeIds from content: {}", e.getMessage());
        }
        return ids;
    }

    private Map<String, String> extractRouteDescriptions(
            String fileName, String content, String serviceId) {
        Map<String, String> descriptions = new HashMap<>();
        try {
            descriptions.putAll(
                    getRouteDocumentStrategy(fileName)
                            .extractRouteDescriptions(content, serviceId));
        } catch (Exception e) {
            log.warn("Could not extract route descriptions from content: {}", e.getMessage());
        }
        return descriptions;
    }

    private RouteDocumentStrategy getRouteDocumentStrategy(String fileName) {
        return routeDocumentStrategies.stream()
                .filter(strategy -> strategy.supports(fileName))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("Unsupported file type: " + fileName));
    }

    private java.util.UUID parseUuid(String str) {
        try {
            return java.util.UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return java.util.UUID.nameUUIDFromBytes(str.getBytes());
        }
    }

    public Optional<RouteVersion> getActiveOrSpecifiedVersion(String serviceId, Integer version) {
        List<RouteVersion> versions = getVersionsByServiceId(serviceId);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        if (version != null) {
            return versions.stream().filter(v -> v.getVersion() == version).findFirst();
        } else {
            Optional<RouteVersion> active =
                    versions.stream().filter(RouteVersion::isActive).findFirst();
            if (active.isPresent()) {
                return active;
            }
            Optional<RouteVersion> autoRestore =
                    versions.stream().filter(RouteVersion::isAutoRestore).findFirst();
            if (autoRestore.isPresent()) {
                return autoRestore;
            }
            return Optional.of(versions.get(versions.size() - 1));
        }
    }

    public Optional<RouteVersion> getActiveOrSpecifiedVersionWithContent(
            String serviceId, Integer version) {
        return getActiveOrSpecifiedVersion(serviceId, version)
                .map(
                        v -> {
                            try {
                                String content = getContentFromDisk(v.getId());
                                v.setContent(content);
                            } catch (IOException e) {
                                log.warn(
                                        "Failed to load content for version {} of service {}: {}",
                                        v.getId(),
                                        serviceId,
                                        e.getMessage());
                            }
                            return v;
                        });
    }
}
