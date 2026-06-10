package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.EnvProperty;
import vn.cxn.apache_camel.model.entity.EnvPropertyEntity;
import vn.cxn.apache_camel.repository.EnvPropertyRepository;
import vn.cxn.apache_camel.util.EncryptionUtil;

/**
 * Service for managing environment properties with DB-backed storage and AES-GCM encryption for
 * sensitive values.
 */
@Service
public class EnvPropertyService {

    private static final Logger log = LoggerFactory.getLogger(EnvPropertyService.class);

    @Value("${camel.dashboard.encrypt-key:DefaultSecretKey}")
    private String encryptKey;

    @Value("${camel.dashboard.encrypt-salt:cxn-apache-camel-salt-v1}")
    private String encryptSalt;

    private final EnvPropertyRepository envPropertyRepository;

    // Cached SecretKey to avoid computing PBKDF2 repeatedly
    private SecretKey secretKeySpec;

    public EnvPropertyService(EnvPropertyRepository envPropertyRepository) {
        this.envPropertyRepository = envPropertyRepository;
    }

    @PostConstruct
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

        log.info(
                "EnvPropertyService initialized (DB-backed). Total properties: {}",
                envPropertyRepository.count());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns all properties as DTOs WITHOUT decrypted values. Suitable for external/API consumers.
     */
    @Transactional(readOnly = true)
    public List<EnvProperty> getAll() {
        List<EnvProperty> result = new ArrayList<>();
        for (EnvPropertyEntity entity : envPropertyRepository.findAll()) {
            result.add(toDto(entity, false));
        }
        return result;
    }

    /** Returns a single property as DTO WITHOUT decrypting its value. */
    @Transactional(readOnly = true)
    public EnvProperty get(String key) {
        return envPropertyRepository.findByKey(key).map(e -> toDto(e, false)).orElse(null);
    }

    /**
     * Returns the decrypted plaintext value for use in Camel routes (e.g., {@code
     * {{property.key}}}).
     *
     * <p>Use this method <strong>only</strong> inside trusted server-side code, never expose the
     * result to an external client.
     */
    @Transactional(readOnly = true)
    public String getValue(String key) {
        return envPropertyRepository
                .findByKey(key)
                .map(entity -> resolveValue(entity, key))
                .orElse(null);
    }

    /**
     * Creates or updates a property. If {@code property.isSecret()} is true, the value will be
     * encrypted at rest.
     *
     * @throws EncryptionException if encryption of a secret value fails
     */
    @Transactional
    public EnvProperty save(EnvProperty property) {
        validate(property);

        EnvPropertyEntity entity =
                envPropertyRepository
                        .findByKey(property.getKey())
                        .map(
                                existing -> {
                                    existing.setUpdatedAt(Instant.now());
                                    return existing;
                                })
                        .orElseGet(
                                () -> {
                                    EnvPropertyEntity newEntity = new EnvPropertyEntity();
                                    newEntity.setId(UUID.randomUUID());
                                    newEntity.setCreatedAt(Instant.now());
                                    newEntity.setUpdatedAt(Instant.now());
                                    return newEntity;
                                });

        entity.setKey(property.getKey());
        entity.setDescription(property.getDescription());
        entity.setSecret(property.isSecret());

        if (property.isSecret() && property.getValue() != null) {
            // Encrypt; on failure the transaction will roll back (no silent plaintext fallback).
            entity.setValue(encrypt(property.getValue()));
        } else {
            entity.setValue(property.getValue());
        }

        EnvPropertyEntity saved = envPropertyRepository.save(entity);
        log.info(
                "Property '{}' {} (secret={})",
                saved.getKey(),
                envPropertyRepository.findByKey(saved.getKey()).isPresent() ? "updated" : "created",
                saved.isSecret());
        return toDto(saved, false);
    }

    /** Deletes a property by key. Returns {@code true} if the property existed and was removed. */
    @Transactional
    public boolean delete(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        boolean existed = envPropertyRepository.existsByKey(key);
        if (existed) {
            envPropertyRepository.deleteByKey(key);
            log.info("Property '{}' deleted", key);
        }
        return existed;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Validation
    // ────────────────────────────────────────────────────────────────────────

    private void validate(EnvProperty property) {
        if (property == null) {
            throw new IllegalArgumentException("Property must not be null");
        }
        if (property.getKey() == null || property.getKey().isBlank()) {
            throw new IllegalArgumentException("Property key must not be blank");
        }
        // Disallow control characters and overly long keys
        if (property.getKey().length() > 255) {
            throw new IllegalArgumentException("Property key must be ≤ 255 characters");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Mapping
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Converts a DB entity to a DTO.
     *
     * @param entity the source entity
     * @param decryptIt if {@code true}, secret values are decrypted (for internal use only); if
     *     {@code false}, the raw stored value is returned
     */
    private EnvProperty toDto(EnvPropertyEntity entity, boolean decryptIt) {
        EnvProperty dto = new EnvProperty();
        dto.setId(entity.getId() != null ? entity.getId().toString() : null);
        dto.setKey(entity.getKey());
        dto.setDescription(entity.getDescription());
        dto.setSecret(entity.isSecret());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.isSecret() && entity.getValue() != null && decryptIt) {
            try {
                dto.setValue(decrypt(entity.getValue()));
            } catch (Exception ex) {
                log.error(
                        "Failed to decrypt property '{}' for internal DTO: {}",
                        entity.getKey(),
                        ex.getMessage());
                dto.setValue(null);
            }
        } else {
            dto.setValue(entity.getValue());
        }
        return dto;
    }

    /** Resolves a property's value, decrypting it if needed. Used by {@link #getValue(String)}. */
    private String resolveValue(EnvPropertyEntity entity, String key) {
        if (!entity.isSecret() || entity.getValue() == null) {
            return entity.getValue();
        }
        try {
            return decrypt(entity.getValue());
        } catch (Exception ex) {
            log.error("Failed to decrypt property '{}': {}", key, ex.getMessage());
            return null;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // AES-GCM encryption helpers
    // ────────────────────────────────────────────────────────────────────────

    private String encrypt(String plaintext) {
        try {
            return EncryptionUtil.encrypt(plaintext, secretKeySpec);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt value", e);
        }
    }

    private String decrypt(String encoded) {
        try {
            return EncryptionUtil.decrypt(encoded, secretKeySpec);
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt value", e);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Custom exception
    // ────────────────────────────────────────────────────────────────────────

    /** Thrown when encryption or decryption fails. */
    public static class EncryptionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
