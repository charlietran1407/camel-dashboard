package vn.cxn.apache_camel.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompressionUtilTest {

    @Test
    void testCompressAndDecompress() {
        String original = "from: timer:tick\n  steps:\n  - to: caffeine-cache://test";
        String compressed = CompressionUtil.compress(original);

        assertThat(compressed).isNotEqualTo(original);

        String decompressed = CompressionUtil.decompress(compressed);
        assertThat(decompressed).isEqualTo(original);
    }

    @Test
    void testNullOrBlankHandling() {
        assertThat(CompressionUtil.compress(null)).isNull();
        assertThat(CompressionUtil.compress("")).isEmpty();
        assertThat(CompressionUtil.compress("   ")).isEqualTo("   ");

        assertThat(CompressionUtil.decompress(null)).isNull();
        assertThat(CompressionUtil.decompress("")).isEmpty();
        assertThat(CompressionUtil.decompress("   ")).isEqualTo("   ");
    }
}
