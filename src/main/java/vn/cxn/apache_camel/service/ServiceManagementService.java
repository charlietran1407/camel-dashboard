package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.RouteInfo;
import vn.cxn.apache_camel.model.dto.ServiceDTO;
import vn.cxn.apache_camel.model.entity.ServiceEntity;
import vn.cxn.apache_camel.repository.ServiceRepository;
import vn.cxn.apache_camel.service.mapper.ServiceMapper;
import vn.cxn.apache_camel.util.CamelRouteUtil;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class ServiceManagementService {

    private static final Logger log = LoggerFactory.getLogger(ServiceManagementService.class);

    private final ServiceRepository serviceRepository;
    private final CamelRouteService camelRouteService;
    private final RouteVersionService versionService;
    private final RouteStateService routeStateService;
    private final SystemLogService systemLogService;
    private final ServiceMapper serviceMapper;

    public ServiceManagementService(
            ServiceRepository serviceRepository,
            CamelRouteService camelRouteService,
            RouteVersionService versionService,
            RouteStateService routeStateService,
            SystemLogService systemLogService,
            ServiceMapper serviceMapper) {
        this.serviceRepository = serviceRepository;
        this.camelRouteService = camelRouteService;
        this.versionService = versionService;
        this.routeStateService = routeStateService;
        this.systemLogService = systemLogService;
        this.serviceMapper = serviceMapper;
    }

    @PostConstruct
    public void init() {
        log.info("ServiceManagementService initialized with PostgreSQL backend.");
    }

    public List<ServiceDTO> getAllServices() {
        List<ServiceEntity> entities = serviceRepository.findAll();
        List<ServiceDTO> list = new ArrayList<>();
        List<RouteInfo> allLiveRoutes = null;
        try {
            allLiveRoutes = camelRouteService.listRoutes();
        } catch (Exception e) {
            log.warn("Failed to list routes during getAllServices: {}", e.getMessage());
        }
        for (ServiceEntity entity : entities) {
            ServiceDTO s = serviceMapper.toDto(entity);
            syncServiceRouteIds(s, allLiveRoutes);
            list.add(s);
        }
        return list;
    }

    public Optional<ServiceDTO> getServiceById(String id) {
        try {
            Optional<ServiceEntity> opt = serviceRepository.findById(parseUuid(id));
            if (opt.isPresent()) {
                ServiceDTO s = serviceMapper.toDto(opt.get());
                List<RouteInfo> allLiveRoutes = null;
                try {
                    allLiveRoutes = camelRouteService.listRoutes();
                } catch (Exception e) {
                    log.warn("Failed to list routes during getServiceById: {}", e.getMessage());
                }
                syncServiceRouteIds(s, allLiveRoutes);
                return Optional.of(s);
            }
        } catch (Exception e) {
            log.error("Failed to get service by ID: {}", id, e);
        }
        return Optional.empty();
    }

    @Transactional
    @Deprecated
    public void updateServiceRouteIds(String serviceId, List<String> routeIds) {
        try {
            Optional<ServiceEntity> opt = serviceRepository.findById(parseUuid(serviceId));
            if (opt.isPresent()) {
                ServiceEntity s = opt.get();
                s.setUpdatedAt(Instant.now());
                serviceRepository.save(s);
                log.info("Updated timestamp for service '{}' on deploy", serviceId);
            }
        } catch (Exception e) {
            log.error("Failed to update service routeIds timestamp: {}", serviceId, e);
        }
    }

    @Transactional
    public ServiceDTO createService(String name, String description) {
        ServiceEntity entity = new ServiceEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setDescription(description);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        ServiceEntity saved = serviceRepository.save(entity);
        log.info("Created ServiceEntity with name: {}", name);
        try {
            systemLogService.log(
                    "AUDIT",
                    "SUCCESS",
                    saved.getId().toString(),
                    "Created Service: " + name,
                    null,
                    null,
                    null);
        } catch (Exception e) {
            log.warn("Failed to audit service creation log: {}", e.getMessage());
        }
        return serviceMapper.toDto(saved);
    }

    @Transactional
    public ServiceDTO upsertService(String id, String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service name is required");
        }

        boolean isEdit = false;
        if (id != null && !id.isBlank()) {
            try {
                isEdit = serviceRepository.existsById(parseUuid(id));
            } catch (Exception ignored) {
            }
        }

        ServiceEntity entity = null;
        if (id != null && !id.isBlank()) {
            try {
                Optional<ServiceEntity> opt = serviceRepository.findById(parseUuid(id));
                if (opt.isPresent()) {
                    entity = opt.get();
                    entity.setName(name);
                    entity.setDescription(description != null ? description : "");
                    entity.setUpdatedAt(Instant.now());
                }
            } catch (Exception ignored) {
            }
        }

        if (entity == null) {
            Optional<ServiceEntity> optByName = serviceRepository.findByNameIgnoreCase(name);
            if (optByName.isPresent()) {
                entity = optByName.get();
                entity.setDescription(description != null ? description : "");
                entity.setUpdatedAt(Instant.now());
                isEdit = true;
            }
        }

        if (entity == null) {
            entity = new ServiceEntity();
            entity.setId(UUID.randomUUID());
            entity.setName(name);
            entity.setDescription(description != null ? description : "");
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            isEdit = false;
        }

        ServiceEntity saved = serviceRepository.save(entity);

        try {
            String actionName = isEdit ? "Updated" : "Created";
            systemLogService.log(
                    "AUDIT",
                    "SUCCESS",
                    saved.getId().toString(),
                    actionName + " Service: " + name,
                    null,
                    null,
                    null);
        } catch (Exception e) {
            log.warn("Failed to audit service upsert log: {}", e.getMessage());
        }

        ServiceDTO s = serviceMapper.toDto(saved);
        List<RouteInfo> allLiveRoutes = null;
        try {
            allLiveRoutes = camelRouteService.listRoutes();
        } catch (Exception e) {
            log.warn("Failed to list routes during upsertService: {}", e.getMessage());
        }
        syncServiceRouteIds(s, allLiveRoutes);
        return s;
    }

    @Transactional
    public boolean deleteService(String id) {
        UUID serviceUuid;
        try {
            serviceUuid = parseUuid(id);
        } catch (IllegalArgumentException e) {
            return false;
        }

        Optional<ServiceEntity> opt = serviceRepository.findById(serviceUuid);
        if (opt.isEmpty()) {
            return false;
        }

        // Collect all potential route IDs for this service to ensure complete cleanup
        Set<String> idsToClean = new HashSet<>();
        try {
            List<String> serviceRouteIds = versionService.getRouteIdsForService(id);
            if (serviceRouteIds != null) {
                idsToClean.addAll(serviceRouteIds);
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to retrieve route IDs from versionService for service '{}': {}",
                    id,
                    e.getMessage());
        }

        // Safety net: scan CamelContext for any route matching the prefix
        String managedPrefix = CamelRouteUtil.getServicePrefix(id);
        try {
            camelRouteService
                    .listRoutes()
                    .forEach(
                            r -> {
                                if (r.id() != null
                                        && (r.id().startsWith(managedPrefix)
                                                || idsToClean.contains(r.id()))) {
                                    idsToClean.add(r.id());
                                }
                            });
        } catch (Exception e) {
            log.warn("Failed to scan live routes for service prefix: {}", e.getMessage());
        }

        // Enforce that NO route in the service is running (in Started state) before
        // deletion
        List<RouteInfo> allRoutes = camelRouteService.listRoutes();
        boolean hasRunningRoute =
                allRoutes.stream()
                        .filter(r -> idsToClean.contains(r.id()))
                        .anyMatch(r -> "Started".equalsIgnoreCase(r.status()));

        if (hasRunningRoute) {
            throw new IllegalStateException("Cannot delete service with running routes");
        }

        // Clean up REST definitions for this service and its route IDs first
        try {
            camelRouteService.cleanUpRestDefinitions(id, idsToClean);
        } catch (Exception e) {
            log.warn(
                    "Failed to clean up REST definitions for service '{}' during deletion: {}",
                    id,
                    e.getMessage());
        }

        // Clean up and remove all routes from CamelContext memory
        log.info(
                "Cleaning up {} route(s) from CamelContext memory for service '{}'",
                idsToClean.size(),
                id);
        for (String routeId : idsToClean) {
            try {
                if (camelRouteService.getRouteStatus(routeId) != "NOT_FOUND") {
                    log.info(
                            "Stopping and removing route '{}' from camelContext during service '{}'"
                                    + " deletion",
                            routeId,
                            id);
                    try {
                        camelRouteService.stopRoute(routeId);
                    } catch (Exception e) {
                        log.warn("Failed to stop route '{}': {}", routeId, e.getMessage());
                    }
                    camelRouteService.removeRoute(routeId);
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to clean up route '{}' from CamelContext during service '{}'"
                                + " deletion: {}",
                        routeId,
                        id,
                        e.getMessage());
            }
        }

        versionService.deleteVersionsByServiceId(id);
        serviceRepository.deleteById(serviceUuid);

        // Clean up all persisted route states for this service
        routeStateService.removeStatesWithPrefix(managedPrefix);

        try {
            systemLogService.log(
                    "AUDIT",
                    "SUCCESS",
                    id,
                    "Deleted Service: " + opt.get().getName(),
                    null,
                    null,
                    null);
        } catch (Exception e) {
            log.warn("Failed to audit service deletion log: {}", e.getMessage());
        }

        return true;
    }

    private void syncServiceRouteIds(ServiceDTO s, List<RouteInfo> allLiveRoutes) {
        if (s == null) return;

        Set<String> liveRouteIds = new LinkedHashSet<>();

        // 1. Get any live routes from CamelContext that belong to this service by
        // prefix or by current serviceId
        if (allLiveRoutes != null && !allLiveRoutes.isEmpty()) {
            try {
                String prefix = CamelRouteUtil.getServicePrefix(s.getId());
                allLiveRoutes.forEach(
                        r -> {
                            if (r.id() != null
                                    && (r.id().startsWith(prefix)
                                            || s.getId().equals(r.serviceId()))) {
                                liveRouteIds.add(r.id());
                            }
                        });
            } catch (Exception e) {
                log.warn(
                        "Failed to filter live route IDs for service '{}': {}",
                        s.getId(),
                        e.getMessage());
            }
        }

        // 2. If live routes are running, prioritize them. Otherwise, fallback to the
        // auto-restore version's route IDs.
        if (!liveRouteIds.isEmpty()) {
            s.setRouteIds(new ArrayList<>(liveRouteIds));
        }
    }

    private java.util.UUID parseUuid(String str) {
        try {
            return java.util.UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return java.util.UUID.nameUUIDFromBytes(str.getBytes());
        }
    }
}
