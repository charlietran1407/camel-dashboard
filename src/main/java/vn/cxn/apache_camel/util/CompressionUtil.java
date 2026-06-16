package vn.cxn.apache_camel.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class CompressionUtil {

    private CompressionUtil() {
        // Prevent instantiation
    }

    /** Compresses the input string using GZIP and encodes it in Base64. */
    public static String compress(String str) {
        if (str == null || str.isBlank()) {
            return str;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
                gzos.write(str.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to compress string", e);
        }
    }

    /** Decompresses the input string using GZIP after decoding from Base64. */
    public static String decompress(String str) {
        if (str == null || str.isBlank()) {
            return str;
        }
        try {
            byte[] compressedBytes = Base64.getDecoder().decode(str);
            ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
            try (GZIPInputStream gzis = new GZIPInputStream(bis)) {
                return new String(gzis.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to decompress string", e);
        }
    }
}
