package vn.cxn.apache_camel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.cxn.apache_camel.model.dto.RestParamInfo;
import vn.cxn.apache_camel.service.route_helper.RestParamExtractorImpl;

class CamelRouteServiceTest {

    @Test
    void testExtractRestParamsFromYaml() {
        String yamlContent =
                """
                - beans:
                    - name: memoryLogsMap
                      type: java.util.concurrent.ConcurrentHashMap

                - rest:
                    id: rest-system-logs-pure-camel
                    path: /api/logs
                    get:
                      - id: get-logs-pure
                        description: "Retrieve system logs optionally filtered by type (e.g. ?type=DEPLOY)"
                        produces: "application/json"
                        param:
                          - name: type
                            type: query
                            dataType: string
                            required: false
                            description: "Filter logs by type (e.g. DEPLOY, SYSTEM, ERROR). If omitted, returns all logs."
                        to:
                          uri: direct:get-logs-pure
                    post:
                      - id: create-log-pure
                        description: "Insert a new log in-memory and write to persistent storage"
                        consumes: "application/json"
                        produces: "application/json"
                        param:
                          - name: body
                            type: body
                            required: true
                            description: "JSON payload representing the log entry to create"
                        to:
                          uri: direct:create-log-pure
                    delete:
                      - id: delete-logs-pure
                        description: "Clear logs of a specific type or all if not specified"
                        produces: "application/json"
                        param:
                          - name: type
                            type: query
                            dataType: string
                            required: false
                            description: "Specific log type to clear (e.g. DEPLOY). If omitted, clears all logs."
                        to:
                          uri: direct:delete-logs-pure
                """;

        String originalId = "route-get-logs-pure";
        String sourceUri =
                "rest://get:/api/logs?consumerComponentName=platform-http&description=Retrieve+system+logs+optionally+filtered+by+type+%28e.g.+%3Ftype%3DDEPLOY%29&produces=application%2Fjson&routeId=svc_8eec97f1-6e9d-4809-aea4-9d51d6e80ae2__route-get-logs-pure";
        List<RestParamInfo> restParams =
                vn.cxn.apache_camel.util.CamelYamlParser.parseRestParams(
                        yamlContent, originalId, sourceUri);

        System.out.println("Extracted Params: " + restParams);
        assertThat(restParams).isNotEmpty();
        assertThat(restParams.get(0).name()).isEqualTo("type");
    }

    @Test
    void testStripMetadata() {
        String yamlContent =
                """
                - beans:
                    - name: myHikariDb
                      type: com.zaxxer.hikari.HikariDataSource
                      properties:
                        jdbcUrl: jdbc:postgresql://localhost:5432/my_db
                    - name: myCustomProcessor
                      type: vn.cxn.MyProcessor
                - metadata:
                    connections:
                      - dbId: demoDb
                - route:
                    id: my-route
                """;

        String clean = vn.cxn.apache_camel.util.CamelYamlParser.stripMetadata(yamlContent);
        System.out.println("Clean YAML:\n" + clean);

        assertThat(clean).contains("myCustomProcessor");
        assertThat(clean).contains("MyProcessor");
        assertThat(clean).contains("my-route");
        assertThat(clean).doesNotContain("myHikariDb");
        assertThat(clean).doesNotContain("HikariDataSource");
        assertThat(clean).doesNotContain("metadata");
    }

    @Test
    void testNormalizeRestPathForComparison() {
        RestParamExtractorImpl extractor = new RestParamExtractorImpl(null, null, null);
        assertThat(extractor.normalizeRestPathForComparison("/users:/%7Bid%7D"))
                .isEqualTo("/users/{id}");
        assertThat(extractor.normalizeRestPathForComparison("/users/{id}"))
                .isEqualTo("/users/{id}");
        assertThat(extractor.normalizeRestPathForComparison("users:/{id}")).isEqualTo("users/{id}");
        assertThat(extractor.normalizeRestPathForComparison("/api-doc")).isEqualTo("/api-doc");
    }

    @Test
    void testIsRestSourceUriMatching() {
        RestParamExtractorImpl extractor = new RestParamExtractorImpl(null, null, null);

        // Match PUT users:/{id}
        String sourceUri1 =
                "rest://put:/users:/%7Bid%7D?consumerComponentName=platform-http&consumes=application%2Fjson&produces=application%2Fjson&routeId=route-2663";
        assertThat(extractor.isRestSourceUriMatching(sourceUri1, "put", "/users/{id}")).isTrue();
        assertThat(extractor.isRestSourceUriMatching(sourceUri1, "get", "/users/{id}")).isFalse();
        assertThat(extractor.isRestSourceUriMatching(sourceUri1, "put", "/users")).isFalse();

        // Match GET users
        String sourceUri2 = "rest://get:/users?consumerComponentName=platform-http";
        assertThat(extractor.isRestSourceUriMatching(sourceUri2, "get", "/users")).isTrue();

        // Match Swagger api-doc
        String sourceUri3 = "rest-api:///api-doc?consumerComponentName=platform-http";
        assertThat(extractor.isRestSourceUriMatching(sourceUri3, "get", "/api-doc")).isTrue();
    }

    @Test
    void testIsRestSourceUriMatchingOpenApi() {
        RestParamExtractorImpl extractor = new RestParamExtractorImpl(null, null, null);

        // Match Swagger openapi spec
        String sourceUri1 = "rest-openapi:///openapi.json?consumerComponentName=platform-http";
        assertThat(extractor.isRestSourceUriMatching(sourceUri1, "get", "/openapi.json")).isTrue();

        String sourceUri2 = "rest-openapi:openapi.json";
        assertThat(extractor.isRestSourceUriMatching(sourceUri2, "get", "/openapi.json")).isTrue();
    }

    @Test
    void testExtractPathFromRestUriOpenApi() {
        assertThat(
                        vn.cxn.apache_camel.util.CamelRouteUtil.extractPathFromRestUri(
                                "rest-openapi:///openapi.json"))
                .isEqualTo("/openapi.json");
        assertThat(
                        vn.cxn.apache_camel.util.CamelRouteUtil.extractPathFromRestUri(
                                "rest-openapi:openapi.json"))
                .isEqualTo("openapi.json");
    }
}
