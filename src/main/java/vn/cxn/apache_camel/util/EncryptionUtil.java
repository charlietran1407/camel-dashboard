package vn.cxn.apache_camel.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class EncryptionUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int AES_KEY_LENGTH_BITS = 256;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65536;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private EncryptionUtil() {
        // Prevent instantiation
    }

    /** Derives a 256-bit AES key from password using PBKDF2. */
    public static SecretKey deriveSecretKey(String password, String salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        PBEKeySpec spec =
                new PBEKeySpec(
                        password.toCharArray(),
                        salt.getBytes(StandardCharsets.UTF_8),
                        PBKDF2_ITERATIONS,
                        AES_KEY_LENGTH_BITS);
        try {
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Encrypts the given plaintext. The output format is: {@code Base64( IV(12 bytes) ||
     * ciphertext_with_tag )}.
     */
    public static String encrypt(String plaintext, SecretKey secretKeySpec) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(
                Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /** Decrypts a value previously produced by {@link #encrypt(String, SecretKey)}. */
    public static String decrypt(String encoded, SecretKey secretKeySpec) throws Exception {
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
                Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);

        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
