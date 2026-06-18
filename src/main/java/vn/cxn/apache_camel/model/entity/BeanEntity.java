package vn.cxn.apache_camel.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beans")
public class BeanEntity extends BaseAuditEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "bean_name", nullable = false, unique = true, length = 255)
    private String beanName;

    @Column(name = "class_name", nullable = false, length = 255)
    private String className;

    @Column(name = "jar_file_name", nullable = false, length = 255)
    private String jarFileName;

    @Column(name = "jar_data", nullable = false)
    private byte[] jarData;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "registered", nullable = false)
    private boolean registered;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "registered_at")
    private Instant registeredAt;

    public BeanEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getJarFileName() {
        return jarFileName;
    }

    public void setJarFileName(String jarFileName) {
        this.jarFileName = jarFileName;
    }

    public byte[] getJarData() {
        return jarData;
    }

    public void setJarData(byte[] jarData) {
        this.jarData = jarData;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }
}
