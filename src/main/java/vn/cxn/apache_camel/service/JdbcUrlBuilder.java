package vn.cxn.apache_camel.service;

import java.util.Objects;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;

@Component
public class JdbcUrlBuilder {

    public String build(DbConnectionEntity conn) {
        Objects.requireNonNull(conn, "Database connection entity must not be null");

        String type = conn.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Database type must not be null or blank");
        }

        String host = conn.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Database host must not be null or blank");
        }

        String database = conn.getDatabaseName();
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("Database name must not be null or blank");
        }

        DatabaseType dbType = DatabaseType.fromKey(type);

        int portVal =
                (conn.getPort() != null && conn.getPort() > 0)
                        ? conn.getPort()
                        : dbType.getDefaultPort();

        String rawUrl =
                dbType.formatUrl(
                        host.trim(),
                        portVal,
                        database.trim(),
                        conn.getUsername(),
                        conn.getPassword());

        return appendQueryOptions(rawUrl, conn.getQueryOptions(), dbType);
    }

    private String appendQueryOptions(String url, String queryOptions, DatabaseType dbType) {
        if (queryOptions == null || queryOptions.isBlank()) {
            return url;
        }
        String options = queryOptions.trim();
        if (DatabaseType.MSSQL == dbType || DatabaseType.SQLSERVER == dbType) {
            if (!options.startsWith(";")) {
                url += ";";
            }
            url += options;
        } else {
            if (url.contains("?")) {
                if (!options.startsWith("&") && !options.startsWith("?")) {
                    url += "&";
                } else if (options.startsWith("?")) {
                    options = options.substring(1);
                    if (!url.endsWith("?") && !url.endsWith("&")) {
                        url += "&";
                    }
                }
                url += options;
            } else {
                if (!options.startsWith("?")) {
                    url += "?";
                }
                url += options;
            }
        }
        return url;
    }
}
