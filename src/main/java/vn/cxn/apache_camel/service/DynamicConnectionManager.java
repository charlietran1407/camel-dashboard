package vn.cxn.apache_camel.service;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.service.dynamic_component.DynamicComponentFactory;

@Service
public class DynamicConnectionManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DynamicConnectionManager.class);

    private final CamelContext camelContext;
    private final DynamicComponentFactory dynamicComponentFactory;

    // dbId -> DataSource connection pool
    private final Map<String, DataSource> registryCache = new ConcurrentHashMap<>();

    // dbId -> MongoClient instance
    private final Map<String, MongoClient> mongoRegistryCache = new ConcurrentHashMap<>();

    // dbId -> Set of associated routeIds
    private final Map<String, Set<String>> dbRouteAssociations = new ConcurrentHashMap<>();

    private record DbConfig(String url, String user, String pass) {}

    // dbId -> connection parameters configuration
    private final Map<String, DbConfig> configCache = new ConcurrentHashMap<>();

    public DynamicConnectionManager(
            CamelContext camelContext, DynamicComponentFactory dynamicComponentFactory) {
        this.camelContext = camelContext;
        this.dynamicComponentFactory = dynamicComponentFactory;
    }

    /**
     * Registers or updates a dynamic database component in CamelContext, and tracks route
     * association. Explicitly uses HikariDataSource for high-performance connection pooling.
     *
     * @param dbId the unique component ID (e.g. "billingDb")
     * @param url the JDBC connection URL
     * @param user the database username
     * @param pass the database password
     * @param routeId the ID of the route registering/using this database
     */
    public synchronized void registerDatabase(
            String dbId, String url, String user, String pass, String routeId) {
        if (dbId == null || dbId.isBlank()) {
            throw new IllegalArgumentException("Database Component ID (dbId) must not be blank.");
        }

        log.info(
                "[Connection] Register - Start - Registering database connection pool for component"
                        + " '{}' (URL: {}), associated with Route: {}",
                dbId,
                url,
                routeId);
        boolean isMongo =
                url != null && (url.startsWith("mongodb://") || url.startsWith("mongodb+srv://"));

        // Track route association
        if (routeId != null && !routeId.isBlank()) {
            dbRouteAssociations
                    .computeIfAbsent(dbId, k -> ConcurrentHashMap.newKeySet())
                    .add(routeId);
        }

        // Prevent redundant registrations if already cached with the exact same configuration
        // details
        DbConfig newConfig = new DbConfig(url, user, pass);
        DbConfig cachedConfig = configCache.get(dbId);
        if (cachedConfig != null && cachedConfig.equals(newConfig)) {
            if (isMongo && mongoRegistryCache.containsKey(dbId)) {
                log.info(
                        "[Connection] Register - Detail - MongoDB component '{}' is already"
                                + " registered and active with the same configuration.",
                        dbId);
                return;
            } else if (!isMongo && registryCache.containsKey(dbId)) {
                log.info(
                        "[Connection] Register - Detail - Database component '{}' is already"
                                + " registered and active with the same configuration.",
                        dbId);
                return;
            }
        }

        try {
            // Safely stop and unbind existing component if it exists
            if (camelContext.getComponent(dbId, false) != null) {
                log.warn(
                        "[Connection] Register - Warn - Camel component '{}' already exists."
                                + " Overwriting component registration.",
                        dbId);
                camelContext.removeComponent(dbId);
                try {
                    camelContext.getRegistry().unbind(dbId);
                } catch (Exception e) {
                    log.warn(
                            "[Connection] Register - Warn - Failed to unbind registry bean for"
                                    + " '{}': {}",
                            dbId,
                            e.getMessage());
                }
            }

            if (isMongo) {
                MongoClient mongoClient = dynamicComponentFactory.createMongoClient(url);
                try {
                    camelContext.addComponent(
                            dbId, dynamicComponentFactory.createMongoDbComponent(mongoClient));
                    camelContext.getRegistry().bind(dbId, mongoClient);

                    MongoClient oldClient = mongoRegistryCache.put(dbId, mongoClient);
                    if (oldClient != null) {
                        try {
                            oldClient.close();
                        } catch (Exception e) {
                            log.warn(
                                    "[Connection] Close - Failed - MongoClient for '{}': {}",
                                    dbId,
                                    e.getMessage());
                        }
                    }

                    configCache.put(dbId, newConfig);
                    log.info(
                            "[Connection] Register - Success - MongoDB component '{}' in"
                                    + " CamelContext.",
                            dbId);
                } catch (Exception e) {
                    try {
                        mongoClient.close();
                    } catch (Exception ex) {
                        log.warn(
                                "[Connection] Close - Failed - MongoClient for '{}' during"
                                        + " rollback: {}",
                                dbId,
                                ex.getMessage());
                    }
                    throw e;
                }
            } else {
                DataSource dataSource = dynamicComponentFactory.createDataSource(url, user, pass);
                try {
                    camelContext.addComponent(
                            dbId, dynamicComponentFactory.createSqlComponent(dataSource));
                    camelContext.getRegistry().bind(dbId, dataSource);

                    // Cache the active connection pool
                    DataSource oldDataSource = registryCache.put(dbId, dataSource);
                    closeDataSource(oldDataSource);

                    configCache.put(dbId, newConfig);
                    log.info(
                            "[Connection] Register - Success - SQL database component '{}' in"
                                    + " CamelContext.",
                            dbId);
                } catch (Exception e) {
                    closeDataSource(dataSource);
                    throw e;
                }
            }
        } catch (Exception e) {
            log.error(
                    "[Connection] Register - Failed - Failed to register dynamic database component"
                            + " '{}': {}",
                    dbId,
                    e.getMessage(),
                    e);
            throw new RuntimeException("Dynamic registration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Clean up all connections registered for a given route. If a database connection has no
     * remaining associated routes, it is automatically closed.
     */
    public synchronized void cleanupConnectionsForRoute(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return;
        }

        log.info("[Connection] Cleanup - Start - Route: {}", routeId);

        java.util.List<String> toUnregister = new java.util.ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : dbRouteAssociations.entrySet()) {
            String dbId = entry.getKey();
            Set<String> routes = entry.getValue();

            if (routes.remove(routeId)) {
                log.info(
                        "[Connection] Cleanup - Detail - Removed Route reference '{}' from database"
                                + " component '{}'. Remaining references: {}",
                        routeId,
                        dbId,
                        routes.size());

                if (routes.isEmpty()) {
                    toUnregister.add(dbId);
                }
            }
        }

        for (String dbId : toUnregister) {
            log.info(
                    "[Connection] Cleanup - Detail - Database component '{}' has zero remaining"
                            + " active route associations. Automatically unregistering.",
                    dbId);
            unregisterDatabase(dbId);
        }
    }

    /** Unregisters a database component and cleanly closes its resources. */
    public synchronized void unregisterDatabase(String dbId) {
        if (dbId == null || dbId.isBlank()) {
            return;
        }

        log.info(
                "[Connection] Unregister - Start - Unregistering dynamic database component and"
                        + " registry bean: '{}'",
                dbId);
        try {
            camelContext.removeComponent(dbId);
            try {
                camelContext.getRegistry().unbind(dbId);
            } catch (Exception e) {
                log.warn(
                        "[Connection] Unregister - Warn - Failed to unbind registry bean for '{}':"
                                + " {}",
                        dbId,
                        e.getMessage());
            }
            dbRouteAssociations.remove(dbId);
            configCache.remove(dbId);
            DataSource dataSource = registryCache.remove(dbId);
            closeDataSource(dataSource);

            MongoClient mongoClient = mongoRegistryCache.remove(dbId);
            if (mongoClient != null) {
                mongoClient.close();
                log.info(
                        "[Connection] Unregister - Success - Closed MongoClient connection for"
                                + " '{}'",
                        dbId);
            }
        } catch (Exception e) {
            log.warn(
                    "[Connection] Unregister - Failed - Error encountered while unregistering"
                            + " component '{}': {}",
                    dbId,
                    e.getMessage());
        }
    }

    /** Safely closes the database connection pool using standard or reflection-based closures. */
    private void closeDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        try {
            if (dataSource instanceof AutoCloseable autoCloseable) {
                autoCloseable.close();
                log.info("Closed DataSource using AutoCloseable implementation.");
            } else {
                try {
                    java.lang.reflect.Method closeMethod = dataSource.getClass().getMethod("close");
                    closeMethod.invoke(dataSource);
                    log.info("Closed DataSource via reflection invocation of close().");
                } catch (NoSuchMethodException ignored) {
                    log.debug(
                            "DataSource class {} does not contain a close() method.",
                            dataSource.getClass().getName());
                }
            }
        } catch (Exception e) {
            log.error("Failed to cleanly close dynamic DataSource: {}", e.getMessage());
        }
    }

    @Override
    @PreDestroy
    public void close() {
        log.info(
                "Shutting down DynamicConnectionManager. Closing all registered database pools...");
        Set<String> sqlDbIds = new java.util.HashSet<>(registryCache.keySet());
        for (String dbId : sqlDbIds) {
            unregisterDatabase(dbId);
        }
        Set<String> mongoDbIds = new java.util.HashSet<>(mongoRegistryCache.keySet());
        for (String dbId : mongoDbIds) {
            unregisterDatabase(dbId);
        }
    }
}
