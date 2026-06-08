package vn.cxn.apache_camel.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class SystemLog extends BaseModel {
    private String type;
    private String status;
    private String target;
    private String message;
    private Integer version;
    private String versionId;
    private String fileName;

    private String instanceId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant timestamp;

    public SystemLog() {}

    public SystemLog(
            String id,
            String type,
            String status,
            String target,
            String message,
            Integer version,
            String versionId,
            String fileName) {
        this(id, type, status, target, message, version, versionId, fileName, null);
    }

    public SystemLog(
            String id,
            String type,
            String status,
            String target,
            String message,
            Integer version,
            String versionId,
            String fileName,
            String instanceId) {
        this.setId(id);
        this.type = type;
        this.status = status;
        this.target = target;
        this.message = message;
        this.version = version;
        this.versionId = versionId;
        this.fileName = fileName;
        this.instanceId = instanceId;
        this.timestamp = Instant.now();
        this.setCreatedAt(this.timestamp);
        this.setUpdatedAt(this.timestamp);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
