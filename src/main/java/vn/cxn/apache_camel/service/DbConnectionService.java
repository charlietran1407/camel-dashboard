package vn.cxn.apache_camel.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.exception.ConnectionTestException;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;
import vn.cxn.apache_camel.repository.DbConnectionRepository;

@Service
public class DbConnectionService {

    private static final Logger log = LoggerFactory.getLogger(DbConnectionService.class);

    private final DbConnectionRepository repository;
    private final DynamicConnectionManager connectionManager;
    private final JdbcUrlBuilder jdbcUrlBuilder;

    public DbConnectionService(
            DbConnectionRepository repository,
            DynamicConnectionManager connectionManager,
            JdbcUrlBuilder jdbcUrlBuilder) {
        this.repository = repository;
        this.connectionManager = connectionManager;
        this.jdbcUrlBuilder = jdbcUrlBuilder;
    }

    /** Automatically restore all database connection pools on startup. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void initGlobalConnections() {
        log.info("Starting global database connection pools initialization...");
        repository.findAll().stream()
                .forEach(
                        conn -> {
                            try {
                                registerConnectionInManager(conn);
                                log.info(
                                        "  ✓ Initialized connection pool for '{}'", conn.getDbId());
                            } catch (Exception e) {
                                log.error(
                                        "  ✗ Failed to initialize connection pool for '{}': {}",
                                        conn.getDbId(),
                                        maskSensitiveInfo(e.getMessage()));
                            }
                        });
        log.info("Global database connection pools initialization complete.");
    }

    @Transactional(readOnly = true)
    public List<DbConnectionEntity> getAllConnections() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<DbConnectionEntity> getConnectionById(UUID id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<DbConnectionEntity> getConnectionByDbId(String dbId) {
        return repository.findByDbId(dbId);
    }

    @Transactional
    public DbConnectionEntity saveConnection(DbConnectionEntity connection) {
        if (connection.getId() == null) {
            connection.setId(UUID.randomUUID());
        }

        try {
            DbConnectionEntity saved = repository.saveAndFlush(connection);
            // Instantly register/update the pool in the Camel Context!
            registerConnectionInManager(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.debug(
                    "Unique constraint violation during connection save: {}",
                    maskSensitiveInfo(e.getMessage()),
                    e);
            throw new IllegalArgumentException(
                    "Connection ID '" + connection.getDbId() + "' already exists.", e);
        }
    }

    @Transactional(readOnly = true)
    public void testConnection(DbConnectionEntity connection) {
        String url = jdbcUrlBuilder.build(connection);
        boolean isMongo =
                url != null && (url.startsWith("mongodb://") || url.startsWith("mongodb+srv://"));

        if (isMongo) {
            try (com.mongodb.client.MongoClient mongoClient =
                    com.mongodb.client.MongoClients.create(url)) {
                String dbName =
                        (connection.getDatabaseName() != null
                                        && !connection.getDatabaseName().isBlank())
                                ? connection.getDatabaseName()
                                : "admin";
                mongoClient.getDatabase(dbName).runCommand(new Document("ping", 1));
            } catch (Exception e) {
                log.debug(
                        "MongoDB connection test failed: {}", maskSensitiveInfo(e.getMessage()), e);
                throw new ConnectionTestException(
                        "Connection test failed: " + maskSensitiveInfo(e.getMessage()));
            }
        } else {
            try (Connection conn =
                            DriverManager.getConnection(
                                    url, connection.getUsername(), connection.getPassword());
                    Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            } catch (Exception e) {
                log.debug("SQL connection test failed: {}", maskSensitiveInfo(e.getMessage()), e);
                throw new ConnectionTestException(
                        "Connection test failed: " + maskSensitiveInfo(e.getMessage()));
            }
        }
    }

    @Transactional
    public void deleteConnection(UUID id) {
        Optional<DbConnectionEntity> existing = repository.findById(id);
        if (existing.isPresent()) {
            DbConnectionEntity conn = existing.get();
            repository.delete(conn);

            // Instantly unregister and close the connection pool!
            connectionManager.unregisterDatabase(conn.getDbId());
            log.info("Deleted connection profile and stopped pool for '{}'", conn.getDbId());
        }
    }

    private void registerConnectionInManager(DbConnectionEntity conn) {
        String url = jdbcUrlBuilder.build(conn);
        // Since it is a global database connection configured via UI, we register it
        // with a null routeId
        connectionManager.registerDatabase(
                conn.getDbId(), url, conn.getUsername(), conn.getPassword(), null);
    }

    String maskSensitiveInfo(String input) {
        if (input == null) {
            return null;
        }
        String masked = input.replaceAll("(?i)password=[^&\\s;]*", "password=****");
        masked = masked.replaceAll("(mongodb(?:\\+srv)?://[^:]+:)[^@\\s]+(@)", "$1****$2");
        return masked;
    }
}
