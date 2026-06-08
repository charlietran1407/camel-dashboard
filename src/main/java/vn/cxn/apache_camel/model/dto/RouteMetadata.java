package vn.cxn.apache_camel.model.dto;

import java.time.Instant;

public record RouteMetadata(
        String originalId,
        String serviceId,
        String description,
        int activeVersion,
        int totalVersions,
        boolean persistent,
        Instant createdAt,
        Instant updatedAt,
        Instant deployedAt) {}
