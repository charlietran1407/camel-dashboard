package vn.cxn.apache_camel.service.route_document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteIdManagerTest {

    @Test
    void testCommentPreservation() {
        String yamlContent =
                """
                # camel-dashboard: dependency=dev.langchain4j:langchain4j-google-ai-gemini:0.31.0, org.apache.camel:camel-langchain4j-embeddings:4.20.0
                - from:
                    uri: direct:start
                    steps:
                      - log: "Hello World"
                """;
        RouteIdManager routeIdManager = new RouteIdManager();
        String ensured = routeIdManager.ensureRouteIds(yamlContent);
        assertThat(ensured).startsWith("# camel-dashboard: dependency=");

        Map<String, String> mapping = Map.of("route_1", "route_new");
        String rewritten = routeIdManager.rewriteRouteIds(yamlContent, mapping, "prefix_");
        assertThat(rewritten).startsWith("# camel-dashboard: dependency=");
    }
}
