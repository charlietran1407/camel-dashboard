package vn.cxn.apache_camel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.mapper.RouteVersionMapperImpl;
import vn.cxn.apache_camel.service.route_document.YamlRouteDocumentStrategyImpl;
import vn.cxn.apache_camel.validation.RouteValidationResult;

class RouteValidationServiceTest {

    private RouteValidationService validationService;
    private CamelContext mainCamelContext;
    private RouteVersionService routeVersionService;
    private DynamicBeanService dynamicBeanService;
    private EnvPropertyService envPropertyService;

    @BeforeEach
    void setUp() {
        mainCamelContext = new DefaultCamelContext();

        vn.cxn.apache_camel.repository.ServiceRepository serviceRepo =
                Mockito.mock(vn.cxn.apache_camel.repository.ServiceRepository.class);
        vn.cxn.apache_camel.repository.RouteVersionRepository versionRepo =
                Mockito.mock(vn.cxn.apache_camel.repository.RouteVersionRepository.class);

        Mockito.when(serviceRepo.findById(Mockito.any(java.util.UUID.class)))
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
        Mockito.when(versionRepo.findById(Mockito.any(java.util.UUID.class)))
                .thenAnswer(
                        invocation -> {
                            java.util.UUID uuid = invocation.getArgument(0);
                            return java.util.Optional.ofNullable(versionsMap.get(uuid));
                        });

        Mockito.when(
                        versionRepo.save(
                                Mockito.any(
                                        vn.cxn.apache_camel.model.entity.RouteVersionEntity.class)))
                .thenAnswer(
                        invocation -> {
                            vn.cxn.apache_camel.model.entity.RouteVersionEntity entity =
                                    invocation.getArgument(0);
                            versionsMap.put(entity.getId(), entity);
                            return entity;
                        });

        Mockito.when(versionRepo.findByServiceId(Mockito.any(java.util.UUID.class)))
                .thenAnswer(
                        invocation -> {
                            java.util.UUID svcId = invocation.getArgument(0);
                            return versionsMap.values().stream()
                                    .filter(v -> v.getService().getId().equals(svcId))
                                    .collect(java.util.stream.Collectors.toList());
                        });

        Mockito.when(versionRepo.findAll())
                .thenAnswer(invocation -> new java.util.ArrayList<>(versionsMap.values()));

        Mockito.when(versionRepo.findAllByOrderByServiceIdAndVersionDesc())
                .thenAnswer(invocation -> new java.util.ArrayList<>(versionsMap.values()));

        Mockito.when(versionRepo.findAllSummary())
                .thenAnswer(
                        invocation ->
                                versionsMap.values().stream()
                                        .map(this::toSummary)
                                        .collect(java.util.stream.Collectors.toList()));

        Mockito.when(versionRepo.findSummaryByServiceId(Mockito.any(java.util.UUID.class)))
                .thenAnswer(
                        invocation -> {
                            java.util.UUID svcId = invocation.getArgument(0);
                            return versionsMap.values().stream()
                                    .filter(v -> v.getService().getId().equals(svcId))
                                    .map(this::toSummary)
                                    .collect(java.util.stream.Collectors.toList());
                        });

        RouteStateService routeStateService = Mockito.mock(RouteStateService.class);
        vn.cxn.apache_camel.repository.RouteRepository routeRepo =
                Mockito.mock(vn.cxn.apache_camel.repository.RouteRepository.class);
        ClusterNodeService clusterNodeService = Mockito.mock(ClusterNodeService.class);
        org.springframework.beans.factory.ObjectProvider<
                        org.springframework.data.redis.core.RedisTemplate<String, Object>>
                redisTemplateProvider =
                        Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        routeVersionService =
                new RouteVersionService(
                        versionRepo,
                        serviceRepo,
                        routeRepo,
                        mainCamelContext,
                        routeStateService,
                        clusterNodeService,
                        redisTemplateProvider,
                        java.util.List.of(new YamlRouteDocumentStrategyImpl()),
                        new RouteVersionMapperImpl(
                                new com.fasterxml.jackson.databind.ObjectMapper(),
                                mainCamelContext,
                                routeRepo));
        routeVersionService.init();

        dynamicBeanService = Mockito.mock(DynamicBeanService.class);
        envPropertyService = Mockito.mock(EnvPropertyService.class);

        validationService =
                new RouteValidationService(
                        mainCamelContext,
                        routeVersionService,
                        dynamicBeanService,
                        envPropertyService);
    }

    @Test
    void validatesValidYamlSyntax() {
        String yaml =
                """
                - route:
                    id: validRoute
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "hello"
                """;

        RouteValidationResult result =
                validationService.validate(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "validRoute.yaml", yaml, "FAST");

        assertThat(result.getIsValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void rejectsInvalidYamlSyntax() {
        String yaml =
                """
                - route:
                    id: invalidRoute
                  from:
                    uri: timer:tick
                  - log:
                  this is totally broken yaml
                """;

        RouteValidationResult result =
                validationService.validate(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "invalidRoute.yaml", yaml, "FAST");

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getStage()).isEqualTo("SYNTAX_STAGE");
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("DSL_PARSE_ERROR");
        assertThat(result.getErrors().get(0).getLocation()).isNotNull();
    }

    @Test
    void reportsWarningForMissingPropertiesAndBeans() {
        String yaml =
                """
                - route:
                    id: warningRoute
                    from:
                      uri: timer:tick
                      steps:
                        - bean:
                            ref: missingBean
                        - log:
                            message: "Config value is {{missing.config.key}}"
                """;

        when(envPropertyService.get("missing.config.key")).thenReturn(null);
        when(dynamicBeanService.getAllBeans()).thenReturn(new ArrayList<>());

        RouteValidationResult result =
                validationService.validate(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "warningRoute.yaml", yaml, "FAST");

        assertThat(result.getIsValid()).isTrue(); // Warnings do not make validation fail
        assertThat(result.getWarnings()).hasSize(2);
        assertThat(result.getWarnings().stream().map(w -> w.getCode()))
                .containsExactlyInAnyOrder(
                        "MISSING_PROPERTY_REFERENCE", "MISSING_STATIC_BEAN_REFERENCE");
    }

    @Test
    void permitsSameServiceReplacementButBlocksCrossServiceRouteIdConflict() {
        String yaml =
                """
                - route:
                    id: finishedGoodsRoute
                    from:
                      uri: timer:tick
                """;

        // Save an active version for service-a
        RouteVersion activeV1 =
                routeVersionService.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966",
                        "finishedGoodsRoute.yaml",
                        yaml,
                        "");
        routeVersionService.updateAutoRestoreStatus(activeV1.getId(), true);

        // 1. Same-service check (should pass and suggest replacement)
        RouteValidationResult resultSame =
                validationService.validate(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966",
                        "finishedGoodsRoute.yaml",
                        yaml,
                        "FAST");
        assertThat(resultSame.getIsValid()).isTrue();
        assertThat(resultSame.getReplaceCandidates()).containsExactly(activeV1.getId());

        // 2. Cross-service check (should fail)
        RouteValidationResult resultCross =
                validationService.validate(
                        "464017f5-2736-407a-8d34-92e4ffa2481e",
                        "finishedGoodsRoute.yaml",
                        yaml,
                        "FAST");
        assertThat(resultCross.getIsValid()).isFalse();
        assertThat(resultCross.getStage()).isEqualTo("CONFLICT_STAGE");
        assertThat(resultCross.getErrors().get(0).getCode())
                .isEqualTo("CROSS_SERVICE_ROUTE_ID_COLLISION");
    }

    @Test
    void blocksCrossServiceEndpointConflict() {
        String yaml1 =
                """
                - route:
                    id: route1
                    from:
                      uri: platform-http:/orders
                """;
        String yaml2 =
                """
                - route:
                    id: route2
                    from:
                      uri: platform-http:/orders
                """;

        // Save active version for service-a exposing platform-http:/orders
        RouteVersion activeV1 =
                routeVersionService.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "route1.yaml", yaml1, "");
        routeVersionService.updateAutoRestoreStatus(activeV1.getId(), true);

        // Validate service-b trying to expose the same endpoint (should block)
        RouteValidationResult result =
                validationService.validate(
                        "464017f5-2736-407a-8d34-92e4ffa2481e", "route2.yaml", yaml2, "FAST");
        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getStage()).isEqualTo("CONFLICT_STAGE");
        assertThat(result.getErrors().get(0).getCode())
                .isEqualTo("CROSS_SERVICE_ENDPOINT_COLLISION");
    }

    @Test
    void permitsCrossServiceSameApiContextPath() {
        String yaml1 =
                """
                - restConfiguration:
                    component: platform-http
                    contextPath: /cameldash
                    apiContextPath: api-doc
                - route:
                    id: route1
                    from:
                      uri: direct:start
                """;
        String yaml2 =
                """
                - restConfiguration:
                    component: platform-http
                    contextPath: /cameldash
                    apiContextPath: api-doc
                - route:
                    id: route2
                    from:
                      uri: direct:start
                """;

        // Save active version for service-a exposing api-doc under /cameldash
        RouteVersion activeV1 =
                routeVersionService.uploadRoute(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "route1.yaml", yaml1, "");
        routeVersionService.updateAutoRestoreStatus(activeV1.getId(), true);

        // Validate service-b exposing api-doc under /cameldash (should NOT conflict
        // because of
        // apiContextPath exception)
        RouteValidationResult result =
                validationService.validate(
                        "464017f5-2736-407a-8d34-92e4ffa2481e", "route2.yaml", yaml2, "FAST");
        assertThat(result.getIsValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void ignoresHttpMethodsInBeanValidation() {
        String yaml =
                """
                - route:
                    from:
                      uri: rest
                      parameters:
                        method: post
                        path: upload-file
                      steps:
                        - log:
                            message: "hello"
                """;

        RouteValidationResult result =
                validationService.validate(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966", "route.yaml", yaml, "FAST");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getIsValid()).isTrue();
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void validatesYamlWithBeansInPreDeployMode() {
        String yaml =
                """
                - route:
                    id: routeWithBeans
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: bean:myCustomModel?method=length
                - beans:
                    - name: myCustomModel
                      type: java.lang.String
                """;

        RouteValidationResult result =
                validationService.validate(
                        "5e5ae536-07cb-4433-98af-ed4cda2fe966",
                        "routeWithBeans.yaml",
                        yaml,
                        "PRE_DEPLOY");

        assertThat(result.getIsValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
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
                            if (name.equals("getValidateResult")) return entity.getValidateResult();
                            return null;
                        });
    }
}
