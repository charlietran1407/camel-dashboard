package vn.cxn.apache_camel.service.route_helper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.dto.RouteMetadata;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.entity.RouteEntity;
import vn.cxn.apache_camel.model.entity.RouteVersionEntity;
import vn.cxn.apache_camel.repository.RouteRepository;
import vn.cxn.apache_camel.repository.RouteVersionRepository;
import vn.cxn.apache_camel.service.RouteVersionService;

@Service
public class RouteMetadataExtractorImpl implements RouteMetadataExtractor {

    private final RouteRepository routeRepository;
    private final RouteVersionRepository routeVersionRepository;
    private final RouteVersionService versionService;

    public RouteMetadataExtractorImpl(
            RouteRepository routeRepository,
            RouteVersionRepository routeVersionRepository,
            RouteVersionService versionService) {
        this.routeRepository = routeRepository;
        this.routeVersionRepository = routeVersionRepository;
        this.versionService = versionService;
    }

    @Override
    public RouteMetadata extractRouteMetadata(String id, String defaultDescription) {
        int activeVersion = 0;
        Instant deployedAt = null;
        boolean persistent = false;
        Instant createdAt = null;
        Instant updatedAt = null;
        int totalVersions = 0;
        String originalId = id;
        String serviceId = null;
        String description = defaultDescription;

        Optional<RouteEntity> routeEntityOpt = routeRepository.findById(id);
        if (routeEntityOpt.isPresent()) {
            RouteEntity re = routeEntityOpt.get();
            persistent = true;
            createdAt = re.getCreatedAt();
            updatedAt = re.getUpdatedAt();
            originalId = re.getOriginalRouteId();
            if (re.getDescription() != null && !re.getDescription().isBlank()) {
                description = re.getDescription();
            }

            RouteVersionEntity rve = re.getVersion();
            if (rve != null) {
                activeVersion = rve.getVersion();
                deployedAt = rve.getUploadedAt();
                serviceId = rve.getService().getId().toString();
                totalVersions =
                        routeVersionRepository.findByServiceId(rve.getService().getId()).size();
            }
        } else {
            List<RouteVersion> routeVersions = versionService.getVersionsByRouteId(id);
            totalVersions = routeVersions.size();
            Optional<RouteVersion> activeVersionOpt =
                    routeVersions.stream().filter(RouteVersion::isAutoRestore).findFirst();
            if (activeVersionOpt.isPresent()) {
                RouteVersion v = activeVersionOpt.get();
                activeVersion = v.getVersion();
                deployedAt = v.getDeployedAt();
                persistent = true;
                createdAt = v.getCreatedAt();
                updatedAt = v.getUpdatedAt();
                String storedDescription = versionService.getRouteDescription(v, id);
                if (storedDescription != null && !storedDescription.isBlank()) {
                    description = storedDescription;
                }
                originalId = versionService.getOriginalRouteId(v, id);
                serviceId = v.getServiceId();
            }
        }

        if (serviceId == null && id != null && id.startsWith("svc_") && id.contains("__")) {
            serviceId = id.substring(4, id.indexOf("__"));
        }

        return new RouteMetadata(
                originalId,
                serviceId,
                description,
                activeVersion,
                totalVersions,
                persistent,
                createdAt,
                updatedAt,
                deployedAt);
    }
}
