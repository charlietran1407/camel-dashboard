package vn.cxn.apache_camel.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
        name = "db_connections",
        uniqueConstraints = {@UniqueConstraint(columnNames = "db_id")})
public class DbConnectionEntity extends BaseAuditEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "db_id", nullable = false, unique = true, length = 255)
    private String dbId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "database_name", nullable = false, length = 255)
    private String databaseName;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "password", columnDefinition = "TEXT")
    private String password;

    @Column(name = "query_options", length = 512)
    private String queryOptions;

    public DbConnectionEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String dbId) {
        this.dbId = dbId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getQueryOptions() {
        return queryOptions;
    }

    public void setQueryOptions(String queryOptions) {
        this.queryOptions = queryOptions;
    }
}
