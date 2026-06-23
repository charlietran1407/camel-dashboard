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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.exception.ConnectionTestException;
import vn.cxn.apache_camel.model.dto.DbConnectionDTO;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;
import vn.cxn.apache_camel.repository.DbConnectionRepository;
import vn.cxn.apache_camel.service.mapper.DbConnectionMapper;
import vn.cxn.apache_camel.util.EncryptionUtil;

@Service
public class DbConnectionService {

    private static final Logger log = LoggerFactory.getLogger(DbConnectionService.class);

    @Value("${camel.dashboard.encrypt-key:DefaultSecretKey}")
    private String encryptKey;

    @Value("${camel.dashboard.encrypt-salt:cxn-apache-camel-salt-v1}")
    private String encryptSalt;

    private final DbConnectionRepository repository;
    private final DynamicConnectionManager connectionManager;
    private final JdbcUrlBuilder jdbcUrlBuilder;
    private final DbConnectionMapper mapper;

    private javax.crypto.SecretKey secretKeySpec;

    public DbConnectionService(
            DbConnectionRepository repository,
            DynamicConnectionManager connectionManager,
            JdbcUrlBuilder jdbcUrlBuilder,
            DbConnectionMapper mapper) {
        this.repository = repository;
        this.connectionManager = connectionManager;
        this.jdbcUrlBuilder = jdbcUrlBuilder;
        this.mapper = mapper;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (encryptKey == null || encryptKey.isBlank()) {
            throw new IllegalStateException(
                    "camel.dashboard.encrypt-key must be configured. "
                            + "Refusing to start with missing encryption key.");
        }
        try {
            this.secretKeySpec = EncryptionUtil.deriveSecretKey(encryptKey, encryptSalt);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive cryptographic secret key", e);
        }
    }

    /** Automatically restore all database connection pools on startup. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void initGlobalConnections() {
        log.info("Starting global database connection pools initialization...");
        repository
                .findAll()
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
    public List<DbConnectionDTO> getAllConnections() {
        return repository.findAll().stream()
                .map(conn -> mapper.toDto(conn, true))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<DbConnectionDTO> getConnectionById(UUID id) {
        return repository.findById(id).map(conn -> mapper.toDto(conn, true));
    }

    @Transactional(readOnly = true)
    public Optional<DbConnectionDTO> getConnectionByDbId(String dbId) {
        return repository.findByDbId(dbId).map(conn -> mapper.toDto(conn, true));
    }

    @Transactional
    public DbConnectionDTO saveConnection(DbConnectionDTO dto) {
        if (dto.getId() == null || dto.getId().isBlank()) {
            dto.setId(UUID.randomUUID().toString());
        }

        UUID uuid = UUID.fromString(dto.getId());
        Optional<DbConnectionEntity> existingOpt = repository.findById(uuid);

        DbConnectionEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
        } else {
            entity = new DbConnectionEntity();
            entity.setId(uuid);
            entity.setCreatedAt(java.time.Instant.now());
        }

        entity.setDbId(dto.getDbId());
        entity.setType(dto.getType());
        entity.setHost(dto.getHost());
        entity.setPort(dto.getPort());
        entity.setDatabaseName(dto.getDatabaseName());
        entity.setUsername(dto.getUsername());
        entity.setQueryOptions(dto.getQueryOptions());
        entity.setUpdatedAt(java.time.Instant.now());

        String inputPassword = dto.getPassword();
        if (inputPassword == null || inputPassword.equals("••••••••")) {
            if (existingOpt.isPresent()) {
                // Keep the existing encrypted password
                entity.setPassword(existingOpt.get().getPassword());
            } else {
                // If it is a new connection but password is not provided/empty
                entity.setPassword(encrypt(""));
            }
        } else {
            entity.setPassword(encrypt(inputPassword));
        }

        try {
            DbConnectionEntity saved = repository.saveAndFlush(entity);
            // Instantly register/update the pool in the Camel Context!
            registerConnectionInManager(saved);
            return mapper.toDto(saved, true);
        } catch (DataIntegrityViolationException e) {
            log.debug(
                    "Unique constraint violation during connection save: {}",
                    maskSensitiveInfo(e.getMessage()),
                    e);
            throw new IllegalArgumentException(
                    "Connection ID '" + dto.getDbId() + "' already exists.", e);
        }
    }

    @Transactional(readOnly = true)
    public void testConnection(DbConnectionDTO dto) {
        String plainPassword = dto.getPassword();
        if (plainPassword == null || plainPassword.equals("••••••••")) {
            if (dto.getId() != null && !dto.getId().isBlank()) {
                Optional<DbConnectionEntity> existingOpt =
                        repository.findById(UUID.fromString(dto.getId()));
                if (existingOpt.isPresent()) {
                    plainPassword = decrypt(existingOpt.get().getPassword());
                }
            } else if (dto.getDbId() != null && !dto.getDbId().isBlank()) {
                Optional<DbConnectionEntity> existingOpt = repository.findByDbId(dto.getDbId());
                if (existingOpt.isPresent()) {
                    plainPassword = decrypt(existingOpt.get().getPassword());
                }
            }
        }

        DbConnectionEntity temp = new DbConnectionEntity();
        if (dto.getId() != null && !dto.getId().isBlank()) {
            temp.setId(UUID.fromString(dto.getId()));
        }
        temp.setDbId(dto.getDbId());
        temp.setType(dto.getType());
        temp.setHost(dto.getHost());
        temp.setPort(dto.getPort());
        temp.setDatabaseName(dto.getDatabaseName());
        temp.setUsername(dto.getUsername());
        temp.setPassword(plainPassword);
        temp.setQueryOptions(dto.getQueryOptions());

        String url = jdbcUrlBuilder.build(temp);
        boolean isMongo =
                url != null && (url.startsWith("mongodb://") || url.startsWith("mongodb+srv://"));

        if (isMongo) {
            try (com.mongodb.client.MongoClient mongoClient =
                    com.mongodb.client.MongoClients.create(url)) {
                String dbName =
                        (temp.getDatabaseName() != null && !temp.getDatabaseName().isBlank())
                                ? temp.getDatabaseName()
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
                                    url, temp.getUsername(), temp.getPassword());
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
        DbConnectionEntity temp = new DbConnectionEntity();
        temp.setId(conn.getId());
        temp.setDbId(conn.getDbId());
        temp.setType(conn.getType());
        temp.setHost(conn.getHost());
        temp.setPort(conn.getPort());
        temp.setDatabaseName(conn.getDatabaseName());
        temp.setUsername(conn.getUsername());
        temp.setPassword(decrypt(conn.getPassword()));
        temp.setQueryOptions(conn.getQueryOptions());

        String url = jdbcUrlBuilder.build(temp);
        // Since it is a global database connection configured via UI, we register it
        // with a null routeId
        connectionManager.registerDatabase(
                temp.getDbId(), url, temp.getUsername(), temp.getPassword(), null);
    }

    private String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            return EncryptionUtil.encrypt(plaintext, secretKeySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt database password", e);
        }
    }

    private String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            return EncryptionUtil.decrypt(encoded, secretKeySpec);
        } catch (Exception e) {
            log.debug("Failed to decrypt password, assuming plain-text: {}", e.getMessage());
            return encoded;
        }
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
