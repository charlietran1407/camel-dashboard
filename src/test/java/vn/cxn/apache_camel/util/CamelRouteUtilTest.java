package vn.cxn.apache_camel.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.junit.jupiter.api.Test;

class CamelRouteUtilTest {

    @Test
    void testNormalizePath() {
        assertThat(CamelRouteUtil.normalizePath(null)).isEqualTo("/");
        assertThat(CamelRouteUtil.normalizePath("")).isEqualTo("/");
        assertThat(CamelRouteUtil.normalizePath("/api/logs")).isEqualTo("/api/logs");
        assertThat(CamelRouteUtil.normalizePath("\\api\\logs\\")).isEqualTo("/api/logs");
        assertThat(CamelRouteUtil.normalizePath("///api///logs///")).isEqualTo("/api/logs");
    }

    @Test
    void testPathMatchesAny() {
        List<String> paths = List.of("/api/logs", "/orders", "/");

        assertThat(CamelRouteUtil.pathMatchesAny("/api/logs", paths)).isTrue();
        assertThat(CamelRouteUtil.pathMatchesAny("\\api\\logs\\", paths)).isTrue();
        assertThat(CamelRouteUtil.pathMatchesAny("/api/logs/", paths)).isTrue();
        assertThat(CamelRouteUtil.pathMatchesAny("/orders", paths)).isTrue();
        assertThat(CamelRouteUtil.pathMatchesAny("/other", paths)).isFalse();
        assertThat(CamelRouteUtil.pathMatchesAny(null, paths)).isFalse();
        assertThat(CamelRouteUtil.pathMatchesAny("/api/logs", null)).isFalse();
        assertThat(CamelRouteUtil.pathMatchesAny("/api/logs", Collections.emptyList())).isFalse();
    }

    @Test
    void testGetServicePrefix() {
        assertThat(CamelRouteUtil.getServicePrefix(null)).isNull();
        assertThat(CamelRouteUtil.getServicePrefix("")).isNull();
        assertThat(CamelRouteUtil.getServicePrefix("abc")).isEqualTo("svc_abc__");
        assertThat(CamelRouteUtil.getServicePrefix("abc-123_xyz")).isEqualTo("svc_abc-123_xyz__");
    }

    @Test
    void testRouteBelongsToService() {
        String servicePrefix = "svc_service1__";
        List<String> allRouteIds = List.of("svc_service1__route1", "managed_route2");

        // Route belongs via servicePrefix
        assertThat(
                        CamelRouteUtil.routeBelongsToService(
                                "svc_service1__route1", servicePrefix, allRouteIds))
                .isTrue();
        assertThat(
                        CamelRouteUtil.routeBelongsToService(
                                "svc_service1__other", servicePrefix, allRouteIds))
                .isTrue();

        // Route belongs via direct contain in allRouteIds
        assertThat(CamelRouteUtil.routeBelongsToService("managed_route2", null, allRouteIds))
                .isTrue();

        // Route belongs via rId suffix ending with __ + routeId
        assertThat(CamelRouteUtil.routeBelongsToService("route1", null, allRouteIds)).isTrue();

        // Route does not belong
        assertThat(CamelRouteUtil.routeBelongsToService("unrelated_route", null, allRouteIds))
                .isFalse();
        assertThat(CamelRouteUtil.routeBelongsToService(null, servicePrefix, allRouteIds))
                .isFalse();
    }

    @Test
    void testVerbBelongsToService() {
        String serviceId = "service1";
        String servicePrefix = "svc_service1__";
        List<String> allRouteIds = List.of("svc_service1__route1");

        // Dummy VerbDefinition
        VerbDefinition verb = new DummyVerbDefinition();
        verb.setRouteId("svc_service1__route1");

        assertThat(CamelRouteUtil.verbBelongsToService(verb, serviceId, servicePrefix, allRouteIds))
                .isTrue();

        // Try verb belonging via fallback toUri
        VerbDefinition fallbackVerb = new DummyVerbDefinition();
        fallbackVerb.setRouteId("some_other_id");
        ToDefinition to = new ToDefinition("direct:svc_service1__backend");
        fallbackVerb.setTo(to);

        assertThat(
                        CamelRouteUtil.verbBelongsToService(
                                fallbackVerb, serviceId, servicePrefix, allRouteIds))
                .isTrue();

        // Try toUri matching allRouteIds
        VerbDefinition fallbackVerb2 = new DummyVerbDefinition();
        fallbackVerb2.setRouteId("some_other_id2");
        ToDefinition to2 = new ToDefinition("direct:svc_service1__route1");
        fallbackVerb2.setTo(to2);
        assertThat(CamelRouteUtil.verbBelongsToService(fallbackVerb2, null, null, allRouteIds))
                .isTrue();
    }

    @Test
    void testExtractPathFromRestUri() {
        assertThat(CamelRouteUtil.extractPathFromRestUri(null)).isEqualTo("");
        assertThat(CamelRouteUtil.extractPathFromRestUri("rest://get:/api/logs?param1=value"))
                .isEqualTo("/api/logs");
        assertThat(CamelRouteUtil.extractPathFromRestUri("rest:post:/api/orders?a=b&c=d"))
                .isEqualTo("/api/orders");
        assertThat(CamelRouteUtil.extractPathFromRestUri("rest:get:/api/logs"))
                .isEqualTo("/api/logs");
        assertThat(CamelRouteUtil.extractPathFromRestUri("direct:get-logs")).isEqualTo("");
    }

    // Concrete dummy implementation of abstract VerbDefinition for test purposes
    private static class DummyVerbDefinition extends VerbDefinition {
        @Override
        public String asVerb() {
            return "get";
        }
    }
}
