package vn.cxn.apache_camel.model.dto;

import java.time.Instant;
import java.util.List;

public class ServiceDTO extends BaseModel {
    private String name;
    private String description;

    private List<String> routeIds; // Route IDs currently running in this service

    public ServiceDTO() {}

    public ServiceDTO(String id, String name, String description) {
        this.setId(id);
        this.name = name;
        this.description = description;
        this.setCreatedAt(Instant.now());
        this.setUpdatedAt(Instant.now());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getRouteIds() {
        return routeIds;
    }

    public void setRouteIds(List<String> routeIds) {
        this.routeIds = routeIds;
    }
}
