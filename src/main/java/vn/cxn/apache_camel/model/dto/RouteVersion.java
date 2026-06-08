package vn.cxn.apache_camel.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteVersion extends BaseModel {
    private String serviceId;
    private String serviceName;
    private String fileName;
    private String content;
    private List<String> routeIds = new ArrayList<>(); // Runtime route IDs managed by the dashboard
    private List<String> originalRouteIds =
            new ArrayList<>(); // Route IDs as defined in the uploaded file
    private Map<String, String> routeDescriptions =
            new HashMap<>(); // Runtime route ID -> description
    private int version;
    private String description;
    private boolean autoRestore;
    private boolean active;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant uploadedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant deployedAt;

    public RouteVersion() {}

    public RouteVersion(String id, String serviceId, String fileName, String content, int version) {
        this.setId(id);
        this.serviceId = serviceId;
        this.fileName = fileName;
        this.content = content;
        this.version = version;
        this.uploadedAt = Instant.now();
        this.autoRestore = false;
        this.active = false;
        this.routeIds = new ArrayList<>();
        this.originalRouteIds = new ArrayList<>();
        this.routeDescriptions = new HashMap<>();
        super.setCreatedAt(this.uploadedAt);
        super.setUpdatedAt(this.uploadedAt);
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAutoRestore() {
        return autoRestore;
    }

    public void setAutoRestore(boolean autoRestore) {
        this.autoRestore = autoRestore;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    public void setDeployedAt(Instant deployedAt) {
        this.deployedAt = deployedAt;
    }

    public List<String> getRouteIds() {
        return routeIds;
    }

    public void setRouteIds(List<String> routeIds) {
        this.routeIds = routeIds;
    }

    public List<String> getOriginalRouteIds() {
        return originalRouteIds;
    }

    public void setOriginalRouteIds(List<String> originalRouteIds) {
        this.originalRouteIds = originalRouteIds;
    }

    public Map<String, String> getRouteDescriptions() {
        return routeDescriptions;
    }

    public void setRouteDescriptions(Map<String, String> routeDescriptions) {
        this.routeDescriptions = routeDescriptions;
    }

    private List<vn.cxn.apache_camel.validation.ValidationWarning> warnings = new ArrayList<>();

    public List<vn.cxn.apache_camel.validation.ValidationWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<vn.cxn.apache_camel.validation.ValidationWarning> warnings) {
        this.warnings = warnings;
    }
}
