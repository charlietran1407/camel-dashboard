package vn.cxn.apache_camel.service.route_helper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.entity.RouteEntity;
import vn.cxn.apache_camel.model.entity.RouteVersionEntity;
import vn.cxn.apache_camel.repository.RouteRepository;
import vn.cxn.apache_camel.repository.RouteVersionRepository;
import vn.cxn.apache_camel.service.RouteVersionService;

@Service
public class RouteRegistrationServiceImpl implements RouteRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RouteRegistrationServiceImpl.class);

    private final RouteRepository routeRepository;
    private final RouteVersionRepository routeVersionRepository;
    private final RouteVersionService versionService;

    public RouteRegistrationServiceImpl(
            RouteRepository routeRepository,
            RouteVersionRepository routeVersionRepository,
            RouteVersionService versionService) {
        this.routeRepository = routeRepository;
        this.routeVersionRepository = routeVersionRepository;
        this.versionService = versionService;
    }

    @Override
    @Transactional
    public void registerRoutesInDb(RouteVersion version) {
        if (version.getRouteIds() == null) {
            return;
        }
        Optional<RouteVersionEntity> rveOpt =
                routeVersionRepository.findById(parseUuid(version.getId()));
        if (rveOpt.isEmpty()) {
            log.warn("Route version entity not found in DB for ID: {}", version.getId());
            return;
        }
        RouteVersionEntity rve = rveOpt.get();

        version.getRouteIds()
                .forEach(
                        routeId -> {
                            String originalRouteId =
                                    versionService.getOriginalRouteId(version, routeId);
                            String description =
                                    versionService.getRouteDescription(version, routeId);

                            RouteEntity route = routeRepository.findById(routeId).orElse(null);
                            if (route == null) {
                                route = new RouteEntity();
                                route.setRouteId(routeId);
                                route.setCreatedAt(Instant.now());
                                route.setDesiredState("Started");
                            }
                            route.setOriginalRouteId(originalRouteId);
                            route.setVersion(rve);
                            route.setDescription(description);
                            route.setUpdatedAt(Instant.now());
                            routeRepository.save(route);
                            log.info(
                                    "Registered route '{}' with desiredState '{}' in DB",
                                    routeId,
                                    route.getDesiredState());
                        });
    }

    private UUID parseUuid(String str) {
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(str.getBytes());
        }
    }
}
