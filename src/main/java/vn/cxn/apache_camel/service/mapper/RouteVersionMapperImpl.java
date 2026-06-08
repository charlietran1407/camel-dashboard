package vn.cxn.apache_camel.service.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.entity.RouteVersionEntity;
import vn.cxn.apache_camel.repository.RouteRepository;
import vn.cxn.apache_camel.repository.RouteVersionSummary;
import vn.cxn.apache_camel.validation.ValidationWarning;

@Component
public class RouteVersionMapperImpl implements RouteVersionMapper {

    private static final Logger log = LoggerFactory.getLogger(RouteVersionMapperImpl.class);

    private final ObjectMapper objectMapper;
    private final CamelContext camelContext;
    private final RouteRepository routeRepository;

    public RouteVersionMapperImpl(
            ObjectMapper objectMapper,
            @Lazy CamelContext camelContext,
            RouteRepository routeRepository) {
        this.objectMapper = objectMapper;
        this.camelContext = camelContext;
        this.routeRepository = routeRepository;
    }

    @Override
    public RouteVersion toModel(RouteVersionEntity entity) {
        if (entity == null) {
            return null;
        }
        RouteVersion routeVersion = new RouteVersion();
        routeVersion.setId(entity.getId().toString());
        if (entity.getService() != null) {
            try {
                routeVersion.setServiceId(entity.getService().getId().toString());
                routeVersion.setServiceName(entity.getService().getName());
            } catch (Exception e) {
                log.warn(
                        "Failed to get service details from entity for version {}: {}",
                        entity.getId(),
                        e.getMessage());
            }
        }
        routeVersion.setFileName(entity.getFileName());
        routeVersion.setContent(entity.getYamlContent());
        routeVersion.setVersion(entity.getVersion());
        routeVersion.setDescription(entity.getDescription());
        routeVersion.setAutoRestore(entity.getAutoRestore());
        routeVersion.setUploadedAt(entity.getUploadedAt());
        routeVersion.setUpdatedAt(entity.getUpdatedAt());
        routeVersion.setDeployedAt(entity.getDeployedAt());
        routeVersion.setRouteIds(splitCsv(entity.getRouteIds()));
        routeVersion.setOriginalRouteIds(splitCsv(entity.getOriginalRouteIds()));
        routeVersion.setRouteDescriptions(
                readDescriptions(entity.getId(), entity.getRouteDescriptions()));
        routeVersion.setWarnings(readWarnings(entity.getId(), entity.getValidateResult()));
        routeVersion.setActive(isActive(entity.getId(), routeVersion));
        return routeVersion;
    }

    @Override
    public RouteVersion toModel(RouteVersionSummary summary) {
        if (summary == null) {
            return null;
        }
        RouteVersion routeVersion = new RouteVersion();
        routeVersion.setId(summary.getId().toString());
        if (summary.getService() != null) {
            try {
                routeVersion.setServiceId(summary.getService().getId().toString());
                routeVersion.setServiceName(summary.getService().getName());
            } catch (Exception e) {
                log.warn(
                        "Failed to get service details from summary for version {}: {}",
                        summary.getId(),
                        e.getMessage());
            }
        }
        routeVersion.setFileName(summary.getFileName());
        routeVersion.setContent(null);
        routeVersion.setVersion(summary.getVersion());
        routeVersion.setDescription(summary.getDescription());
        routeVersion.setAutoRestore(summary.getAutoRestore());
        routeVersion.setUploadedAt(summary.getUploadedAt());
        routeVersion.setUpdatedAt(summary.getUpdatedAt());
        routeVersion.setDeployedAt(summary.getDeployedAt());
        routeVersion.setRouteIds(splitCsv(summary.getRouteIds()));
        routeVersion.setOriginalRouteIds(splitCsv(summary.getOriginalRouteIds()));
        routeVersion.setRouteDescriptions(
                readDescriptions(summary.getId(), summary.getRouteDescriptions()));
        routeVersion.setWarnings(readWarnings(summary.getId(), summary.getValidateResult()));
        routeVersion.setActive(isActive(summary.getId(), routeVersion));
        return routeVersion;
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.asList(value.split(","));
    }

    private Map<String, String> readDescriptions(UUID versionId, String value) {
        if (value == null || value.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize route descriptions for version {}", versionId, e);
            return new HashMap<>();
        }
    }

    private List<ValidationWarning> readWarnings(UUID versionId, String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<ValidationWarning>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize warnings for version {}", versionId, e);
            return new ArrayList<>();
        }
    }

    private boolean isActive(UUID versionId, RouteVersion routeVersion) {
        if (camelContext == null
                || routeVersion.getRouteIds() == null
                || routeVersion.getRouteIds().isEmpty()) {
            return false;
        }
        boolean isRunning =
                routeVersion.getRouteIds().stream()
                        .anyMatch(routeId -> camelContext.getRoute(routeId) != null);
        if (!isRunning) {
            return false;
        }
        return routeVersion.getRouteIds().stream()
                .map(routeId -> routeRepository.findById(routeId).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(
                        route ->
                                route.getVersion() != null
                                        && route.getVersion().getId().equals(versionId));
    }
}
