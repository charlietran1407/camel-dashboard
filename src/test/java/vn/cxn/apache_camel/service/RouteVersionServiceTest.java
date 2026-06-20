package vn.cxn.apache_camel.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.mapper.RouteVersionMapperImpl;
import vn.cxn.apache_camel.service.route_document.YamlRouteDocumentStrategyImpl;

class RouteVersionServiceTest {

    @Test
    void namespacesRouteIdsPerServiceAndKeepsOriginalRouteId() throws Exception {
        RouteVersionService service = newService();
        String yaml =
                """
                - route:
                    id: FinishedGoodsAPI
                    description: Finished goods API route
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: ok
                """;

        RouteVersion first =
                service.uploadRoute("service-a", "FinishedGoodsAPI.camel.yaml", yaml, "");
        RouteVersion second =
                service.uploadRoute("service-b", "FinishedGoodsAPI.camel.yaml", yaml, "");

        assertThat(first.getOriginalRouteIds()).containsExactly("FinishedGoodsAPI");
        assertThat(second.getOriginalRouteIds()).containsExactly("FinishedGoodsAPI");
        assertThat(first.getRouteIds()).containsExactly("svc_service-a__FinishedGoodsAPI");
        assertThat(second.getRouteIds()).containsExactly("svc_service-b__FinishedGoodsAPI");
        assertThat(first.getRouteIds()).doesNotContainAnyElementsOf(second.getRouteIds());
        assertThat(service.getRouteDescription(first, "svc_service-a__FinishedGoodsAPI"))
                .isEqualTo("Finished goods API route");
    }

    @Test
    void deploymentContentUsesManagedRouteId() throws Exception {
        RouteVersionService service = newService();
        String yaml =
                """
                - route:
                    id: FinishedGoodsAPI
                    from:
                      uri: timer:tick
                """;

        RouteVersion version =
                service.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966",
                        "FinishedGoodsAPI.camel.yaml",
                        yaml,
                        "");

        String deploymentContent = service.getDeploymentContent(version);

        assertThat(deploymentContent)
                .contains("id: svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__FinishedGoodsAPI");
        assertThat(deploymentContent).doesNotContain("id: FinishedGoodsAPI");
    }

    @Test
    void deploymentSignaturesIncludeOriginalRouteIdAndInputEndpoints() throws Exception {
        RouteVersionService service = newService();
        String yaml =
                """
                - route:
                    id: FinishedGoodsAPI
                    from:
                      uri: platform-http:/finished-goods?httpMethodRestrict=GET
                - rest:
                    path: /api
                    get:
                      - path: /finished-goods
                        to: direct:finishedGoods
                """;

        RouteVersion version =
                service.uploadRoute("service-a", "FinishedGoodsAPI.camel.yaml", yaml, "");

        assertThat(service.getDeploymentSignatures(version))
                .contains(
                        "route-id:FinishedGoodsAPI",
                        "endpoint:platform-http:/finished-goods",
                        "rest:/api",
                        "rest:/api/finished-goods");
    }

    @Test
    void rewritesRestVerbIdsAndInternalEndpointsInYaml() throws Exception {
        RouteVersionService service = newService();
        String yaml =
                """
                - rest:
                    path: /api
                    get:
                      - id: get-logs-pure
                        to: direct:get-logs-pure
                - route:
                    id: route-get-logs-pure
                    from:
                      uri: direct:get-logs-pure?delay=1000
                      steps:
                        - to: direct:next-step
                """;

        RouteVersion version =
                service.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "test.camel.yaml", yaml, "");

        assertThat(version.getOriginalRouteIds())
                .containsExactlyInAnyOrder("get-logs-pure", "route-get-logs-pure");
        assertThat(version.getRouteIds())
                .containsExactlyInAnyOrder(
                        "svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__get-logs-pure",
                        "svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__route-get-logs-pure");

        String deploymentContent = service.getDeploymentContent(version);

        // Assert REST verb ID is rewritten so that it maps correctly to the service
        // dashboard
        assertThat(deploymentContent)
                .contains("id: svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__get-logs-pure");
        // Assert route ID is rewritten
        assertThat(deploymentContent)
                .contains("id: svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__route-get-logs-pure");
        // Assert internal endpoints are rewritten and query params are preserved
        assertThat(deploymentContent)
                .contains("to: 'direct:svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__get-logs-pure'");
        assertThat(deploymentContent)
                .contains(
                        "uri: direct:svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__get-logs-pure?delay=1000");
        assertThat(deploymentContent)
                .contains("to: 'direct:svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__next-step'");
    }

    @Test
    void rewritesSplitFormInternalEndpointsInYaml() throws Exception {
        RouteVersionService service = newService();
        String yaml =
                """
                - route:
                    id: route-split-form
                    from:
                      uri: direct
                      parameters:
                        name: start-endpoint
                      steps:
                        - to:
                            uri: direct
                            parameters:
                              name: next-endpoint
                """;

        RouteVersion version =
                service.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "test-split.camel.yaml", yaml, "");

        String deploymentContent = service.getDeploymentContent(version);

        // Assert that the split-form parameters.name gets successfully prefixed
        assertThat(deploymentContent)
                .contains("name: svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__start-endpoint");
        assertThat(deploymentContent)
                .contains("name: svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__next-endpoint");
    }

    @Test
    void rewritesSagaEipEndpointsInYaml() throws Exception {
        RouteVersionService service = newService();
        String yaml =
                """
                - route:
                    id: route-saga
                    from:
                      uri: direct
                      parameters:
                        name: chargeFee
                      steps:
                        - saga:
                            id: saga-123
                            compensation: "direct:refundFee"
                            completion: "direct:completeOrder"
                """;

        RouteVersion version =
                service.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "test-saga.camel.yaml", yaml, "");

        String deploymentContent = service.getDeploymentContent(version);

        // Both compensation and completion URIs should be rewritten with service prefix
        assertThat(deploymentContent)
                .contains(
                        "compensation:"
                                + " 'direct:svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__refundFee'");
        assertThat(deploymentContent)
                .contains(
                        "completion:"
                            + " 'direct:svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__completeOrder'");
    }

    @Test
    void ensureRouteIdsDoesNotGenerateRedundantIdForNestedFrom() {
        YamlRouteDocumentStrategyImpl strategy = new YamlRouteDocumentStrategyImpl();
        String yaml =
                """
                - route:
                from:
                  uri: timer:yaml
                  steps:
                    - setBody:
                        expression:
                          constant:
                            expression: Hello Camel from yaml
                    - circuitBreaker:
                        resilience4jConfiguration:
                          minimumNumberOfCalls: 10
                          failureRateThreshold: 50
                          waitDurationInOpenState: 20
                        steps:
                          - filter:
                              expression:
                                simple:
                                  expression: ${random(10)} > 2
                              steps:
                                - throwException:
                                    message: Forced error
                                    exceptionType: java.lang.IllegalArgumentException
                    - log:
                        message: "${body} (CircuitBreaker is open:
                          ${exchangeProperty.CamelCircuitBreakerResponseShortCircuited})"
                  parameters:
                    period: "1000"
                id: route-1438

                """;

        String result = strategy.ensureRouteIds(yaml);
        // The result should not contain any auto-generated "id: route_" in the from
        // block
        assertThat(result).doesNotContain("route_");
        assertThat(result).contains("id: route-1438");
    }

    @Test
    void ensureRouteIdsGeneratesIdForDirectFrom() {
        YamlRouteDocumentStrategyImpl strategy = new YamlRouteDocumentStrategyImpl();
        String yaml =
                """
                - from:
                    uri: "timer:tick"
                    steps:
                      - to: "caffeine-cache://test"
                """;

        String result = strategy.ensureRouteIds(yaml);
        System.out.println("RESULT:\n" + result);
        assertThat(result).contains("id: route_");
    }

    @Test
    void ensureRouteIdsGeneratesIdForRestVerbs() {
        YamlRouteDocumentStrategyImpl strategy = new YamlRouteDocumentStrategyImpl();
        String yaml =
                """
                - rest:
                    path: /users
                    get:
                      - to: bean:userService?method=findUsers
                      - path: /{id}
                        to: bean:userService?method=findUser(${header.id})
                """;

        String result = strategy.ensureRouteIds(yaml);
        System.out.println("RESULT REST:\n" + result);
        assertThat(result).contains("routeId: route_");
    }

    @Test
    void deploysAndHealsLegacyRouteVersionWithoutRestVerbIds() throws Exception {
        RouteVersionService service = newService();
        String legacyYaml =
                """
                - rest:
                    path: /users
                    get:
                      - to: bean:userService?method=findUsers
                      - path: /{id}
                        to: bean:userService?method=findUser(${header.id})
                """;

        RouteVersion version =
                service.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966",
                        "legacy.camel.yaml",
                        legacyYaml,
                        "");

        // Clear the generated IDs to simulate a legacy database entry created before
        // the fix
        version.setOriginalRouteIds(java.util.List.of("rest-8571"));
        version.setRouteIds(
                java.util.List.of("svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__rest-8571"));

        String deploymentContent = service.getDeploymentContent(version);

        // Verify that the deployment content contains the dynamically healed
        // prefix-rewritten route
        // IDs
        assertThat(deploymentContent)
                .contains("routeId: svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__route_");
        assertThat(deploymentContent).doesNotContain("routeId: route_");
    }

    @Test
    void ensureRouteIdsGeneratesIdForApiContextRouteId() {
        YamlRouteDocumentStrategyImpl strategy = new YamlRouteDocumentStrategyImpl();
        String yaml =
                """
                - restConfiguration:
                    bindingMode: json
                    apiContextPath: /api-doc
                """;

        String result = strategy.ensureRouteIds(yaml);
        System.out.println("RESULT REST CONFIG:\n" + result);
        assertThat(result).contains("apiContextRouteId: route_");
    }

    @Test
    void rewritesApiContextRouteIdInYaml() throws Exception {
        RouteVersionService service = newService();
        String yaml =
                """
                - restConfiguration:
                    bindingMode: json
                    apiContextPath: /api-doc
                    apiContextRouteId: my-api-doc-route
                """;

        RouteVersion version =
                service.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966",
                        "api-doc-test.camel.yaml",
                        yaml,
                        "");

        assertThat(version.getOriginalRouteIds()).containsExactly("my-api-doc-route");
        assertThat(version.getRouteIds())
                .containsExactly("svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__my-api-doc-route");

        String deploymentContent = service.getDeploymentContent(version);
        assertThat(deploymentContent)
                .contains(
                        "apiContextRouteId:"
                                + " svc_5e5ae536-07cb-4433-98af-ed4cda2fe966__my-api-doc-route");
    }

    @Test
    void ensureRouteIdsDoesNotGenerateIdForRestVerbsDelegatingToInternal() {
        YamlRouteDocumentStrategyImpl strategy = new YamlRouteDocumentStrategyImpl();
        String yaml =
                """
                - rest:
                    path: /users
                    put:
                      - path: /{id}
                        to: direct:update-user
                """;

        String result = strategy.ensureRouteIds(yaml);
        System.out.println("RESULT REST DELEGATE:\n" + result);
        assertThat(result).doesNotContain("routeId: route_");
    }

    @Test
    void deploymentSignaturesPrependContextPathAndIncludeApiContextPath() throws Exception {
        RouteVersionService service = newService();
        String yaml =
                """
                - restConfiguration:
                    bindingMode: json
                    contextPath: /api/v2
                    apiContextPath: /api-doc
                - rest:
                    path: /users
                    get:
                      - path: /{id}
                        to: direct:find-user
                """;

        RouteVersion version =
                service.uploadRoute("service-a", "context-path-test.camel.yaml", yaml, "");

        assertThat(service.getDeploymentSignatures(version))
                .contains("rest:/api/v2/api-doc", "rest:/api/v2/users", "rest:/api/v2/users/{id}");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getActiveVersionByRouteIdRetrievesActiveVersionFromRouteEntity() throws Exception {
        vn.cxn.apache_camel.repository.RouteRepository routeRepo =
                org.mockito.Mockito.mock(vn.cxn.apache_camel.repository.RouteRepository.class);
        vn.cxn.apache_camel.repository.ServiceRepository serviceRepo =
                org.mockito.Mockito.mock(vn.cxn.apache_camel.repository.ServiceRepository.class);
        vn.cxn.apache_camel.repository.RouteVersionRepository versionRepo =
                org.mockito.Mockito.mock(
                        vn.cxn.apache_camel.repository.RouteVersionRepository.class);
        org.apache.camel.CamelContext camelContext =
                org.mockito.Mockito.mock(org.apache.camel.CamelContext.class);
        RouteStateService routeStateService = org.mockito.Mockito.mock(RouteStateService.class);
        ClusterNodeService clusterNodeService = org.mockito.Mockito.mock(ClusterNodeService.class);
        org.springframework.beans.factory.ObjectProvider<
                        org.springframework.data.redis.core.RedisTemplate<String, Object>>
                redisTemplateProvider =
                        org.mockito.Mockito.mock(
                                org.springframework.beans.factory.ObjectProvider.class);

        RouteVersionService service =
                new RouteVersionService(
                        versionRepo,
                        serviceRepo,
                        routeRepo,
                        camelContext,
                        routeStateService,
                        clusterNodeService,
                        redisTemplateProvider,
                        java.util.List.of(new YamlRouteDocumentStrategyImpl()),
                        new RouteVersionMapperImpl(
                                new com.fasterxml.jackson.databind.ObjectMapper(),
                                camelContext,
                                routeRepo));
        service.init();

        String routeId = "test-route";
        vn.cxn.apache_camel.model.entity.RouteEntity routeEntity =
                new vn.cxn.apache_camel.model.entity.RouteEntity();
        routeEntity.setRouteId(routeId);

        vn.cxn.apache_camel.model.entity.RouteVersionEntity versionEntity =
                new vn.cxn.apache_camel.model.entity.RouteVersionEntity();
        versionEntity.setId(java.util.UUID.randomUUID());
        versionEntity.setVersion(5);
        versionEntity.setFileName("test-route.camel.yaml");
        versionEntity.setAutoRestore(false);

        vn.cxn.apache_camel.model.entity.ServiceEntity se =
                new vn.cxn.apache_camel.model.entity.ServiceEntity();
        se.setId(java.util.UUID.randomUUID());
        se.setName("test-service");
        versionEntity.setService(se);

        routeEntity.setVersion(versionEntity);

        org.mockito.Mockito.when(routeRepo.findById(routeId))
                .thenReturn(java.util.Optional.of(routeEntity));

        java.util.Optional<RouteVersion> activeOpt = service.getActiveVersionByRouteId(routeId);
        assertThat(activeOpt).isPresent();
        assertThat(activeOpt.get().getVersion()).isEqualTo(5);
        assertThat(activeOpt.get().getFileName()).isEqualTo("test-route.camel.yaml");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getActiveVersionByRouteIdReturnsEmptyWhenRouteNotFound() throws Exception {
        vn.cxn.apache_camel.repository.RouteRepository routeRepo =
                org.mockito.Mockito.mock(vn.cxn.apache_camel.repository.RouteRepository.class);
        vn.cxn.apache_camel.repository.ServiceRepository serviceRepo =
                org.mockito.Mockito.mock(vn.cxn.apache_camel.repository.ServiceRepository.class);
        vn.cxn.apache_camel.repository.RouteVersionRepository versionRepo =
                org.mockito.Mockito.mock(
                        vn.cxn.apache_camel.repository.RouteVersionRepository.class);
        org.apache.camel.CamelContext camelContext =
                org.mockito.Mockito.mock(org.apache.camel.CamelContext.class);
        RouteStateService routeStateService = org.mockito.Mockito.mock(RouteStateService.class);
        ClusterNodeService clusterNodeService = org.mockito.Mockito.mock(ClusterNodeService.class);
        org.springframework.beans.factory.ObjectProvider<
                        org.springframework.data.redis.core.RedisTemplate<String, Object>>
                redisTemplateProvider =
                        org.mockito.Mockito.mock(
                                org.springframework.beans.factory.ObjectProvider.class);

        RouteVersionService service =
                new RouteVersionService(
                        versionRepo,
                        serviceRepo,
                        routeRepo,
                        camelContext,
                        routeStateService,
                        clusterNodeService,
                        redisTemplateProvider,
                        java.util.List.of(new YamlRouteDocumentStrategyImpl()),
                        new RouteVersionMapperImpl(
                                new com.fasterxml.jackson.databind.ObjectMapper(),
                                camelContext,
                                routeRepo));
        service.init();

        String routeId = "test-route";
        org.mockito.Mockito.when(routeRepo.findById(routeId))
                .thenReturn(java.util.Optional.empty());

        java.util.Optional<RouteVersion> activeOpt = service.getActiveVersionByRouteId(routeId);
        assertThat(activeOpt).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private RouteVersionService newService() {
        vn.cxn.apache_camel.repository.ServiceRepository serviceRepo =
                org.mockito.Mockito.mock(vn.cxn.apache_camel.repository.ServiceRepository.class);
        vn.cxn.apache_camel.repository.RouteVersionRepository versionRepo =
                org.mockito.Mockito.mock(
                        vn.cxn.apache_camel.repository.RouteVersionRepository.class);

        org.mockito.Mockito.when(
                        serviceRepo.findById(
                                org.mockito.ArgumentMatchers.any(java.util.UUID.class)))
                .thenAnswer(
                        invocation -> {
                            java.util.UUID uuid = invocation.getArgument(0);
                            vn.cxn.apache_camel.model.entity.ServiceEntity se =
                                    new vn.cxn.apache_camel.model.entity.ServiceEntity();
                            se.setId(uuid);
                            se.setName("Service-" + uuid);
                            return java.util.Optional.of(se);
                        });

        java.util.Map<java.util.UUID, vn.cxn.apache_camel.model.entity.RouteVersionEntity>
                versionsMap = new java.util.HashMap<>();
        org.mockito.Mockito.when(
                        versionRepo.findById(
                                org.mockito.ArgumentMatchers.any(java.util.UUID.class)))
                .thenAnswer(
                        invocation -> {
                            java.util.UUID uuid = invocation.getArgument(0);
                            return java.util.Optional.ofNullable(versionsMap.get(uuid));
                        });

        org.mockito.Mockito.when(
                        versionRepo.save(
                                org.mockito.ArgumentMatchers.any(
                                        vn.cxn.apache_camel.model.entity.RouteVersionEntity.class)))
                .thenAnswer(
                        invocation -> {
                            vn.cxn.apache_camel.model.entity.RouteVersionEntity entity =
                                    invocation.getArgument(0);
                            versionsMap.put(entity.getId(), entity);
                            return entity;
                        });

        org.mockito.Mockito.when(
                        versionRepo.findByServiceId(
                                org.mockito.ArgumentMatchers.any(java.util.UUID.class)))
                .thenAnswer(
                        invocation -> {
                            java.util.UUID svcId = invocation.getArgument(0);
                            return versionsMap.values().stream()
                                    .filter(v -> v.getService().getId().equals(svcId))
                                    .collect(java.util.stream.Collectors.toList());
                        });

        org.mockito.Mockito.when(versionRepo.findAll())
                .thenAnswer(invocation -> new java.util.ArrayList<>(versionsMap.values()));

        org.mockito.Mockito.when(versionRepo.findAllSummary())
                .thenAnswer(
                        invocation ->
                                versionsMap.values().stream()
                                        .map(this::toSummary)
                                        .collect(java.util.stream.Collectors.toList()));

        org.mockito.Mockito.when(
                        versionRepo.findSummaryByServiceId(
                                org.mockito.ArgumentMatchers.any(java.util.UUID.class)))
                .thenAnswer(
                        invocation -> {
                            java.util.UUID svcId = invocation.getArgument(0);
                            return versionsMap.values().stream()
                                    .filter(v -> v.getService().getId().equals(svcId))
                                    .map(this::toSummary)
                                    .collect(java.util.stream.Collectors.toList());
                        });

        org.apache.camel.CamelContext camelContext =
                org.mockito.Mockito.mock(org.apache.camel.CamelContext.class);
        RouteStateService routeStateService = org.mockito.Mockito.mock(RouteStateService.class);
        vn.cxn.apache_camel.repository.RouteRepository routeRepo =
                org.mockito.Mockito.mock(vn.cxn.apache_camel.repository.RouteRepository.class);

        ClusterNodeService clusterNodeService = org.mockito.Mockito.mock(ClusterNodeService.class);
        org.springframework.beans.factory.ObjectProvider<
                        org.springframework.data.redis.core.RedisTemplate<String, Object>>
                redisTemplateProvider =
                        org.mockito.Mockito.mock(
                                org.springframework.beans.factory.ObjectProvider.class);

        RouteVersionService service =
                new RouteVersionService(
                        versionRepo,
                        serviceRepo,
                        routeRepo,
                        camelContext,
                        routeStateService,
                        clusterNodeService,
                        redisTemplateProvider,
                        java.util.List.of(new YamlRouteDocumentStrategyImpl()),
                        new RouteVersionMapperImpl(
                                new com.fasterxml.jackson.databind.ObjectMapper(),
                                camelContext,
                                routeRepo));
        service.init();
        return service;
    }

    private vn.cxn.apache_camel.repository.RouteVersionSummary toSummary(
            vn.cxn.apache_camel.model.entity.RouteVersionEntity entity) {
        return (vn.cxn.apache_camel.repository.RouteVersionSummary)
                java.lang.reflect.Proxy.newProxyInstance(
                        vn.cxn.apache_camel.repository.RouteVersionSummary.class.getClassLoader(),
                        new Class<?>[] {vn.cxn.apache_camel.repository.RouteVersionSummary.class},
                        (proxy, method, args) -> {
                            String name = method.getName();
                            if (name.equals("getId")) return entity.getId();
                            if (name.equals("getService")) return entity.getService();
                            if (name.equals("getFileName")) return entity.getFileName();
                            if (name.equals("getVersion")) return entity.getVersion();
                            if (name.equals("getAutoRestore")) return entity.getAutoRestore();
                            if (name.equals("getUploadedAt")) return entity.getUploadedAt();
                            if (name.equals("getUpdatedAt")) return entity.getUpdatedAt();
                            if (name.equals("getUpdatedBy")) return entity.getUpdatedBy();
                            if (name.equals("getDeployedAt")) return entity.getDeployedAt();
                            if (name.equals("getRouteIds")) return entity.getRouteIds();
                            if (name.equals("getOriginalRouteIds"))
                                return entity.getOriginalRouteIds();
                            if (name.equals("getRouteDescriptions"))
                                return entity.getRouteDescriptions();
                            return null;
                        });
    }

    @Test
    void getActiveOrSpecifiedVersionFallsBackCorrectly() throws Exception {
        RouteVersionService service = newService();

        // 1. empty service versions
        assertThat(service.getActiveOrSpecifiedVersion("empty-service", null)).isEmpty();

        // 2. upload multiple versions
        RouteVersion v2 =
                service.uploadRoute("test-service", "route2.yaml", "- from: timer:tick2", "v2");

        // 3. fetch specified version
        java.util.Optional<RouteVersion> found =
                service.getActiveOrSpecifiedVersion("test-service", 2);
        assertThat(found).isPresent();
        assertThat(found.get().getVersion()).isEqualTo(2);

        // 4. default falls back to latest version (v3) since none is
        // active/auto-restore
        java.util.Optional<RouteVersion> activeFallback =
                service.getActiveOrSpecifiedVersion("test-service", null);
        assertThat(activeFallback).isPresent();
        assertThat(activeFallback.get().getVersion()).isEqualTo(3);

        // 5. check auto-restore fallback
        service.updateAutoRestoreStatus(v2.getId(), true);
        java.util.Optional<RouteVersion> autoRestoreFallback =
                service.getActiveOrSpecifiedVersion("test-service", null);
        assertThat(autoRestoreFallback).isPresent();
        // v2 is now auto-restore, so we fallback to v2
        assertThat(autoRestoreFallback.get().getVersion()).isEqualTo(2);

        // 6. get content test
        java.util.Optional<RouteVersion> contentOpt =
                service.getActiveOrSpecifiedVersionWithContent("test-service", 1);
        assertThat(contentOpt).isPresent();
        assertThat(contentOpt.get().getContent()).isEqualTo("- from: timer:tick1");
    }
}
