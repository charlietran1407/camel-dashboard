package vn.cxn.apache_camel.model.dto;

import java.time.Instant;

public class EnvProperty extends BaseModel {
    private String key;
    private String value;
    private String description;
    private boolean isSecret;

    public EnvProperty() {}

    public EnvProperty(String id, String key, String value, String description) {
        this.setId(id);
        this.key = key;
        this.value = value;
        this.description = description;
        this.setUpdatedAt(Instant.now());
        this.setCreatedAt(Instant.now());
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSecret() {
        return isSecret;
    }

    public void setSecret(boolean secret) {
        isSecret = secret;
    }
}
