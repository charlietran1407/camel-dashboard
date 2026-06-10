package vn.cxn.apache_camel.model.dto;

public class DbConnectionDTO extends BaseModel {
    private String dbId;
    private String type;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private String queryOptions;

    public DbConnectionDTO() {}

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
