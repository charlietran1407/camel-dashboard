package vn.cxn.apache_camel.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CamelYamlUtils} — focusing on the internal endpoint detection and URI rewriting
 * behavior that drives the service-prefix logic in the dashboard.
 *
 * <p>Key behavior documented here:
 *
 * <pre>
 * When a route is deployed as a service, the dashboard calls rewriteInternalUri() on every
 * string value under the keys "uri", "to", "toD", "deadLetterUri".
 *
 * isInternalEndpoint() returns true ONLY when the value starts with "direct:", "seda:", or "vm:".
 *
 * Consequence for YAML authoring:
 *   ✅ Full URI form  →  to: { uri: "direct:channel-email" }
 *         → isInternalEndpoint("direct:channel-email") = true  → gets prefixed
 *
 *   ❌ Split form     →  from: { uri: "direct", parameters: { name: "channel-email" } }
 *         → isInternalEndpoint("direct") = false              → NOT prefixed
 *
 * This means that if a route's "from" uses the split form AND a "to" in another route
 * uses the full-string form for the SAME endpoint name, they will MISMATCH after deployment
 * when the route ID equals the direct endpoint name.
 *
 * Rule: Route ID must differ from the direct endpoint name to avoid prefix collision,
 * because the dashboard only prefixes full-string "direct:xxx" URIs that it encounters in
 * "uri"/"to"/"toD" fields, not the split form used in "from".
 * </pre>
 */
class CamelYamlUtilsTest {

    // ─────────────────────────────────────────────────────────
    // isInternalEndpoint
    // ─────────────────────────────────────────────────────────

    @Test
    void isInternalEndpoint_fullUriForm_returnsTrue() {
        // Full-string form — what "to" steps use
        assertThat(CamelYamlUtils.isInternalEndpoint("direct:channel-email")).isTrue();
        assertThat(CamelYamlUtils.isInternalEndpoint("direct:notify-dlc")).isTrue();
        assertThat(CamelYamlUtils.isInternalEndpoint("seda:processing")).isTrue();
        assertThat(CamelYamlUtils.isInternalEndpoint("vm:internal")).isTrue();
    }

    @Test
    void isInternalEndpoint_splitUriForm_returnsFalse() {
        // Split form — what "from" blocks use: { uri: "direct", parameters: { name: "..." } }
        // The value of the "uri" key is just "direct" without the colon+name,
        // so it does NOT start with "direct:" → NOT considered internal → NOT prefixed.
        assertThat(CamelYamlUtils.isInternalEndpoint("direct")).isFalse();
        assertThat(CamelYamlUtils.isInternalEndpoint("seda")).isFalse();
        assertThat(CamelYamlUtils.isInternalEndpoint("vm")).isFalse();
    }

    @Test
    void isInternalEndpoint_publicSchemes_returnsFalse() {
        assertThat(CamelYamlUtils.isInternalEndpoint("platform-http:/notify")).isFalse();
        assertThat(CamelYamlUtils.isInternalEndpoint("http://api.example.com")).isFalse();
        assertThat(CamelYamlUtils.isInternalEndpoint("rest://post:/api/orders")).isFalse();
        assertThat(CamelYamlUtils.isInternalEndpoint(null)).isFalse();
        assertThat(CamelYamlUtils.isInternalEndpoint("")).isFalse();
    }

    // ─────────────────────────────────────────────────────────
    // rewriteInternalUri
    // ─────────────────────────────────────────────────────────

    @Test
    void rewriteInternalUri_fullUriForm_getsServicePrefix() {
        // Simulates what happens to: { uri: "direct:channel-email" } in a multicast step
        // when deployed as service "svc_abc123__"
        String prefix = "svc_abc123__";

        assertThat(CamelYamlUtils.rewriteInternalUri("direct:channel-email", prefix))
                .isEqualTo("direct:svc_abc123__channel-email");

        assertThat(CamelYamlUtils.rewriteInternalUri("direct:notify-dlc", prefix))
                .isEqualTo("direct:svc_abc123__notify-dlc");

        assertThat(CamelYamlUtils.rewriteInternalUri("seda:processing", prefix))
                .isEqualTo("seda:svc_abc123__processing");

        // double-slash form
        assertThat(CamelYamlUtils.rewriteInternalUri("direct://channel-email", prefix))
                .isEqualTo("direct://svc_abc123__channel-email");
    }

    @Test
    void rewriteInternalUri_splitUriForm_isNotRewritten() {
        // Simulates why from: { uri: "direct", parameters: { name: "channel-email" } }
        // does NOT get prefixed: the value "direct" has no ":" suffix so
        // isInternalEndpoint("direct") == false → rewriteInternalUri returns it unchanged.
        String prefix = "svc_abc123__";

        assertThat(CamelYamlUtils.rewriteInternalUri("direct", prefix)).isEqualTo("direct");
        assertThat(CamelYamlUtils.rewriteInternalUri("seda", prefix)).isEqualTo("seda");
    }

    @Test
    void rewriteInternalUri_alreadyPrefixed_isIdempotent() {
        // If the URI already contains the prefix, it must not be double-prefixed
        String prefix = "svc_abc123__";

        assertThat(CamelYamlUtils.rewriteInternalUri("direct:svc_abc123__channel-email", prefix))
                .isEqualTo("direct:svc_abc123__channel-email");
    }

    @Test
    void rewriteInternalUri_withQueryParams_preservesQuery() {
        String prefix = "svc_abc123__";

        assertThat(CamelYamlUtils.rewriteInternalUri("direct:myEndpoint?timeout=5000", prefix))
                .isEqualTo("direct:svc_abc123__myEndpoint?timeout=5000");
    }

    @Test
    void rewriteInternalUri_nullOrEmpty_returnsUnchanged() {
        String prefix = "svc_abc123__";

        assertThat(CamelYamlUtils.rewriteInternalUri(null, prefix)).isNull();
        assertThat(CamelYamlUtils.rewriteInternalUri("direct:x", null)).isEqualTo("direct:x");
        assertThat(CamelYamlUtils.rewriteInternalUri("direct:x", "")).isEqualTo("direct:x");
    }

    // ─────────────────────────────────────────────────────────
    // The exact mismatch scenario from multicast-dlc.camel.yaml
    // ─────────────────────────────────────────────────────────

    /**
     * Reproduces the root cause of the DirectConsumerNotAvailableException:
     *
     * <p>Given route ID "channel-email" (same as direct endpoint name), the dashboard:
     *
     * <ol>
     *   <li>Prefixes the route ID → "svc_xxx__channel-email"
     *   <li>Rewrites {@code to: uri: "direct:channel-email"} → "direct:svc_xxx__channel-email"
     *       (producer)
     *   <li>Does NOT rewrite {@code from: uri: "direct"} with {@code parameters.name:
     *       "channel-email"} → consumer stays at "direct:channel-email"
     *   <li>Producer → "direct:svc_xxx__channel-email", Consumer → "direct:channel-email" →
     *       MISMATCH
     * </ol>
     *
     * <p>Fix: set route ID to "route-channel-email" (different from direct name "channel-email").
     * Then "direct:channel-email" in "to" is NOT matched as a route ID and therefore not prefixed.
     */
    @Test
    void routeIdCollidesWithDirectName_toUriGetsPrefix_fromDoesNot() {
        String prefix = "svc_abc123__";

        // Simulates the "to" step: uri = "direct:channel-email"
        String toUri = "direct:channel-email";
        String rewrittenToUri = CamelYamlUtils.rewriteInternalUri(toUri, prefix);

        // Simulates the "from" block: uri = "direct" (split form — name comes from parameters)
        String fromUri = "direct"; // value of the "uri" key
        String rewrittenFromUri = CamelYamlUtils.rewriteInternalUri(fromUri, prefix);

        // "to" gets prefixed → producer calls svc_abc123__channel-email
        assertThat(rewrittenToUri).isEqualTo("direct:svc_abc123__channel-email");

        // "from" is NOT prefixed → consumer still listens on "direct" (split form, unchanged)
        // The actual consumer endpoint name comes from parameters.name = "channel-email"
        // → consumer is at "direct://channel-email" → MISMATCH with producer
        assertThat(rewrittenFromUri).isEqualTo("direct");

        // Verify the mismatch
        assertThat(rewrittenToUri)
                .isNotEqualTo("direct:" + rewrittenFromUri.replace("direct", "channel-email"));
    }
}
