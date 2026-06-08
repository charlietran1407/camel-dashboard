package vn.cxn.apache_camel.service;

import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.entity.RouteEntity;
import vn.cxn.apache_camel.repository.RouteRepository;

@Service
@Transactional(readOnly = true)
public class RouteStateService {

    private static final Logger log = LoggerFactory.getLogger(RouteStateService.class);

    private final RouteRepository routeRepository;

    public RouteStateService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    public Optional<String> getDesiredState(String routeId) {
        if (routeId == null) {
            return Optional.empty();
        }
        return routeRepository.findById(routeId).map(RouteEntity::getDesiredState);
    }

    @Transactional
    public void saveDesiredState(String routeId, String state) {
        if (routeId == null || routeId.isBlank() || state == null || state.isBlank()) {
            return;
        }
        routeRepository
                .findById(routeId)
                .ifPresent(
                        route -> {
                            route.setDesiredState(state);
                            route.setUpdatedAt(Instant.now());
                            routeRepository.save(route);
                            log.info(
                                    "Saved desired state '{}' for route '{}' in DB",
                                    state,
                                    routeId);
                        });
    }

    @Transactional
    public void removeDesiredState(String routeId) {
        if (routeId == null) {
            return;
        }
        try {
            if (routeRepository.existsById(routeId)) {
                routeRepository.deleteById(routeId);
                log.info("Removed route '{}' from DB", routeId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete route '{}' from DB: {}", routeId, e.getMessage());
        }
    }

    @Transactional
    public void deleteByVersionId(java.util.UUID versionId) {
        if (versionId == null) {
            return;
        }
        try {
            java.util.List<RouteEntity> routes = routeRepository.findByVersionId(versionId);
            if (!routes.isEmpty()) {
                routeRepository.deleteAll(routes);
                log.info(
                        "Removed {} route(s) associated with version '{}' from DB",
                        routes.size(),
                        versionId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete routes for version '{}': {}", versionId, e.getMessage());
        }
    }

    @Transactional
    public void removeStatesWithPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        try {
            routeRepository.findAll().stream()
                    .filter(r -> r.getRouteId().startsWith(prefix))
                    .forEach(
                            r -> {
                                routeRepository.delete(r);
                                log.info(
                                        "Prefix cleanup: Removed route '{}' from DB",
                                        r.getRouteId());
                            });
        } catch (Exception e) {
            log.warn("Failed to perform prefix cleanup for '{}': {}", prefix, e.getMessage());
        }
    }
}
