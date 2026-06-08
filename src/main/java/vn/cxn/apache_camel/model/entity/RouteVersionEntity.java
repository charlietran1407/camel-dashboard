package vn.cxn.apache_camel.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "route_versions",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_service_version",
                    columnNames = {"service_id", "version"})
        })
public class RouteVersionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "yaml_content", nullable = false, columnDefinition = "TEXT")
    private String yamlContent;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "auto_restore")
    private Boolean autoRestore = false;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "route_ids", columnDefinition = "TEXT")
    private String routeIds;

    @Column(name = "original_route_ids", columnDefinition = "TEXT")
    private String originalRouteIds;

    @Column(name = "route_descriptions", columnDefinition = "TEXT")
    private String routeDescriptions;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "validate_result", columnDefinition = "TEXT")
    private String validateResult;

    public RouteVersionEntity() {}

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRouteIds() {
        return routeIds;
    }

    public void setRouteIds(String routeIds) {
        this.routeIds = routeIds;
    }

    public String getOriginalRouteIds() {
        return originalRouteIds;
    }

    public void setOriginalRouteIds(String originalRouteIds) {
        this.originalRouteIds = originalRouteIds;
    }

    public String getRouteDescriptions() {
        return routeDescriptions;
    }

    public void setRouteDescriptions(String routeDescriptions) {
        this.routeDescriptions = routeDescriptions;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ServiceEntity getService() {
        return service;
    }

    public void setService(ServiceEntity service) {
        this.service = service;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getYamlContent() {
        return yamlContent;
    }

    public void setYamlContent(String yamlContent) {
        this.yamlContent = yamlContent;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getAutoRestore() {
        return autoRestore;
    }

    public void setAutoRestore(Boolean autoRestore) {
        this.autoRestore = autoRestore;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    public void setDeployedAt(Instant deployedAt) {
        this.deployedAt = deployedAt;
    }

    public String getValidateResult() {
        return validateResult;
    }

    public void setValidateResult(String validateResult) {
        this.validateResult = validateResult;
    }
}
