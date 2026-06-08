package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.EnvProperty;
import vn.cxn.apache_camel.model.entity.EnvPropertyEntity;
import vn.cxn.apache_camel.repository.EnvPropertyRepository;

/**
 * Service for managing environment properties with DB-backed storage and AES-GCM encryption for
 * sensitive values.
 */
@Service
public class EnvPropertyService {

    private static final Logger log = LoggerFactory.getLogger(EnvPropertyService.class);

    // AES-GCM parameters
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int AES_KEY_LENGTH_BITS = 256;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // PBKDF2 parameters
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65536;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
            this.secretKeySpec = deriveSecretKey(encryptKey);
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

    /**
     * Encrypts the given plaintext. The output format is: {@code Base64( IV(12 bytes) ||
     * ciphertext_with_tag )}.
     */
    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt value", e);
        }
    }

    /** Decrypts a value previously produced by {@link #encrypt(String)}. */
    private String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length < GCM_IV_LENGTH_BYTES + 16) {
                // 16 = minimum GCM tag length
                throw new IllegalArgumentException("Ciphertext too short");
            }

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt value", e);
        }
    }

    /** Derives a 256-bit AES key from password using PBKDF2. */
    private SecretKey deriveSecretKey(String password) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        PBEKeySpec spec =
                new PBEKeySpec(
                        password.toCharArray(),
                        encryptSalt.getBytes(StandardCharsets.UTF_8),
                        PBKDF2_ITERATIONS,
                        AES_KEY_LENGTH_BITS);
        try {
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        } finally {
            spec.clearPassword();
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
