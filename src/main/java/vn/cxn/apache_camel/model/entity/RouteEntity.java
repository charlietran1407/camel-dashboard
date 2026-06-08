package vn.cxn.apache_camel.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "routes")
public class RouteEntity extends BaseAuditEntity {

    @Id
    @Column(name = "route_id", length = 255)
    private String routeId;

    @Column(name = "original_route_id", nullable = false, length = 255)
    private String originalRouteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private RouteVersionEntity version;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "desired_state", nullable = false, length = 20)
    private String desiredState = "Started";

    public RouteEntity() {}

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getOriginalRouteId() {
        return originalRouteId;
    }

    public void setOriginalRouteId(String originalRouteId) {
        this.originalRouteId = originalRouteId;
    }

    public RouteVersionEntity getVersion() {
        return version;
    }

    public void setVersion(RouteVersionEntity version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDesiredState() {
        return desiredState;
    }

    public void setDesiredState(String desiredState) {
        this.desiredState = desiredState;
    }
}
