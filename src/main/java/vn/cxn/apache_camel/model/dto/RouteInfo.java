package vn.cxn.apache_camel.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RouteInfo(
        String id,
        String originalId,
        String serviceId,
        String status, // "Started", "Stopped", "Suspended"
        String description,
        String sourceUri,
        int activeVersion,
        int totalVersions,
        boolean persistent,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC") Instant createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC") Instant updatedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC") Instant deployedAt,
        List<RestParamInfo> restParams,
        List<Map<String, Object>> nodeStates) {}
