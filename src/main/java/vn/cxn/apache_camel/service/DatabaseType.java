package vn.cxn.apache_camel.service;

public enum DatabaseType {
    POSTGRESQL("postgresql", "jdbc:postgresql", 5432) {
        @Override
        public String formatUrl(
                String host, int port, String database, String username, String password) {
            return String.format("%s://%s:%d/%s", getPrefix(), host, port, database);
        }
    },
    POSTGRES("postgres", "jdbc:postgresql", 5432) {
        @Override
        public String formatUrl(
                String host, int port, String database, String username, String password) {
            return String.format("%s://%s:%d/%s", getPrefix(), host, port, database);
        }
    },
    MYSQL("mysql", "jdbc:mysql", 3306) {
        @Override
        public String formatUrl(
                String host, int port, String database, String username, String password) {
            return String.format("%s://%s:%d/%s", getPrefix(), host, port, database);
        }
    },
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

    public abstract String formatUrl(
            String host, int port, String database, String username, String password);

    public static DatabaseType fromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Database type must not be null");
        }
        String cleanKey = key.toLowerCase().trim();
        for (DatabaseType type : values()) {
            if (type.getKey().equals(cleanKey)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported database type: " + key);
    }
}
