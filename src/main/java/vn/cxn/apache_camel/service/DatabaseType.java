package vn.cxn.apache_camel.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum DatabaseType {
    POSTGRESQL("postgresql", "jdbc:postgresql", 5432),
    POSTGRES("postgres", "jdbc:postgresql", 5432),
    MYSQL("mysql", "jdbc:mysql", 3306),
    MARIADB("mariadb", "jdbc:mariadb", 3306),
    MSSQL("mssql", "jdbc:sqlserver", 1433) {
        @Override
        public String formatUrl(
                String host, int port, String database, String username, String password) {
            return String.format("%s://%s:%d;databaseName=%s", getPrefix(), host, port, database);
        }
    },
    SQLSERVER("sqlserver", "jdbc:sqlserver", 1433) {
        @Override
        public String formatUrl(
                String host, int port, String database, String username, String password) {
            return String.format("%s://%s:%d;databaseName=%s", getPrefix(), host, port, database);
        }
    },
    ORACLE("oracle", "jdbc:oracle:thin", 1521) {
        @Override
        public String formatUrl(
                String host, int port, String database, String username, String password) {
            return String.format("%s:@//%s:%d/%s", getPrefix(), host, port, database);
        }
    },
    MONGODB("mongodb", "mongodb", 27017) {
        @Override
        public String formatUrl(
                String host, int port, String database, String username, String password) {
            if (username != null && !username.isBlank()) {
                return String.format(
                        "%s://%s:%s@%s:%d/%s",
                        getPrefix(),
                        username,
                        password != null ? password : "",
                        host,
                        port,
                        database);
            }
            return String.format("%s://%s:%d/%s", getPrefix(), host, port, database);
        }
    },
    MONGO("mongo", "mongodb", 27017) {
        @Override
        public String formatUrl(
                String host, int port, String database, String username, String password) {
            return MONGODB.formatUrl(host, port, database, username, password);
        }
    };

    private static final Map<String, DatabaseType> BY_KEY;

    static {
        Map<String, DatabaseType> map = new HashMap<>();
        for (DatabaseType type : values()) {
            map.put(type.getKey(), type);
        }
        BY_KEY = Collections.unmodifiableMap(map);
    }

    private final String key;
    private final String prefix;
    private final int defaultPort;

    DatabaseType(String key, String prefix, int defaultPort) {
        this.key = key;
        this.prefix = prefix;
        this.defaultPort = defaultPort;
    }

    public String getKey() {
        return key;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public String formatUrl(
            String host, int port, String database, String username, String password) {
        return String.format("%s://%s:%d/%s", prefix, host, port, database);
    }

    public static DatabaseType fromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Database type must not be null");
        }
        String cleanKey = key.toLowerCase().trim();
        DatabaseType type = BY_KEY.get(cleanKey);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported database type: " + key);
        }
        return type;
    }
}
