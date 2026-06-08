package vn.cxn.apache_camel.repository;

import java.time.Instant;
import java.util.UUID;
import vn.cxn.apache_camel.model.entity.ServiceEntity;

public interface RouteVersionSummary {
    UUID getId();

    ServiceEntity getService();

    String getFileName();

    Integer getVersion();

    Boolean getAutoRestore();

    Instant getUploadedAt();

    Instant getUpdatedAt();

    String getUpdatedBy();

    Instant getDeployedAt();

    String getRouteIds();

    String getOriginalRouteIds();

    String getRouteDescriptions();

    String getDescription();

    String getValidateResult();
}
