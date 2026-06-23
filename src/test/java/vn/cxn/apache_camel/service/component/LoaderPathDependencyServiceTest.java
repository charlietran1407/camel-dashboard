package vn.cxn.apache_camel.service.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.cxn.apache_camel.service.component.CamelYamlComponentScanner.ScanResult;

class LoaderPathDependencyServiceTest {

    private CamelYamlComponentScanner scanner;
    private CamelCatalogCoordinateMapper mapper;
    private LoaderPathDependencyService service;

    @Test
    void testMavenResourceCheck() {
        String resourcePath = "META-INF/maven/org.apache.camel/camel-direct/pom.properties";
        java.net.URL url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        System.out.println("MAVENRESOURCE: " + resourcePath + " -> " + url);
        assertThat(url).isNotNull();
    }

    @BeforeEach
    void setUp() {
        scanner = new CamelYamlComponentScanner();
        mapper = new CamelCatalogCoordinateMapper();
        service = new LoaderPathDependencyService(scanner, mapper);
    }

    @Test
    void testModelineCommentParsing() {
        String yamlContent =
                """
                 # camel-dashboard : dependency = dev.langchain4j:langchain4j-google-ai-gemini:0.31.0,org.apache.camel:camel-langchain4j-embeddings:4.20.0
                 # camel-dashboard:       dependency   =  dev.langchain4j:langchain4j -google-ai-gemini: 1.31.0 , org.apache.camel : camel-langchain4j-embeddings:5.20.0
                 # camel-dashboard :dependency=dev.langchain4j:langchain4j-google-ai-gemini:2.31.0,org.apache.camel:camel-langchain4j-embeddings:6.20.0
                - from:
                    uri: direct:start
                    steps:
                      - log: "Hello World"
                 # camel-dashboard : dependency = dev.langchain4j:langchain4j-google-ai-gemini:3.31.0,org.apache.camel:camel-langchain4j-embeddings:7.20.0
                """;
        ScanResult scanResult = scanner.scan(yamlContent);
        assertThat(scanResult.explicitDependencies())
                .containsExactlyInAnyOrder(
                        "dev.langchain4j:langchain4j-google-ai-gemini:0.31.0",
                        "org.apache.camel:camel-langchain4j-embeddings:4.20.0",
                        "dev.langchain4j:langchain4j-google-ai-gemini:1.31.0",
                        "org.apache.camel:camel-langchain4j-embeddings:5.20.0",
                        "dev.langchain4j:langchain4j-google-ai-gemini:2.31.0",
                        "org.apache.camel:camel-langchain4j-embeddings:6.20.0",
                        "dev.langchain4j:langchain4j-google-ai-gemini:3.31.0",
                        "org.apache.camel:camel-langchain4j-embeddings:7.20.0");
    }

    @Test
    void testClasspathScanPopulated() {
        service.init(); // Explicitly call init
        @SuppressWarnings("unchecked")
        java.util.Set<String> activeArtifacts =
                (java.util.Set<String>)
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                service, "activeClasspathArtifacts");
        assertThat(activeArtifacts).isNotEmpty();
        assertThat(activeArtifacts).contains("org.apache.camel:camel-direct");
    }

    @Test
    void testServiceEnsureComponentsAvailable_OpenApiUser() throws IOException {
        Path yamlPath = Path.of("examples/openapi/user.camel.yaml");
        String yamlContent = Files.readString(yamlPath);

        // Configure dynamic loader path in target directory to avoid polluting root
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "loaderPath", "target/test-camel-ext-libs");

        // Clean up target directory if exists
        java.io.File libDir = new java.io.File("target/test-camel-ext-libs");
        if (libDir.exists()) {
            java.io.File[] files = libDir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    f.delete();
                }
            }
            libDir.delete();
        }

        // Run the service ensureComponentsAvailable
        MissingComponentDownloadResult result = service.ensureComponentsAvailable(yamlContent);

        boolean openApiPresent = false;
        try {
            Class.forName(
                    "org.apache.camel.openapi.RestOpenApiReader",
                    false,
                    Thread.currentThread().getContextClassLoader());
            openApiPresent = true;
        } catch (ClassNotFoundException ignored) {
        }

        if (openApiPresent) {
            assertThat(result.getDownloadedArtifacts())
                    .noneMatch(coord -> coord.contains("camel-openapi-java"));
        } else {
            // Assert findings: openapi-java dependency should be downloaded
            assertThat(result.getDownloadedArtifacts())
                    .anyMatch(
                            coord ->
                                    coord.contains("camel-openapi-java")
                                            && !coord.contains("starter"));
            assertThat(result.isRestartRequired()).isTrue();

            // Check that the file actually exists in the loader path
            java.io.File[] stagedJars =
                    libDir.listFiles(f -> f.getName().contains("camel-openapi-java"));
            assertThat(stagedJars).isNotEmpty();
        }
    }

    @Test
    void testEnsureComponentsAvailable_OpenApiUser() throws IOException {
        // Read the content of user.camel.yaml
        Path yamlPath = Path.of("examples/openapi/user.camel.yaml");
        String yamlContent = Files.readString(yamlPath);

        // Scan the content using our in-memory mock CamelContext parser
        ScanResult scanResult = scanner.scan(yamlContent);

        // Assert scanner findings
        assertThat(scanResult.schemes()).contains("rest", "bean", "direct");
        assertThat(scanResult.languages()).contains("groovy", "simple");
        assertThat(scanResult.dataFormats()).contains("jackson");

        // Resolve missing coordinates using the mapper
        List<String> missingCoords = mapper.resolveMissingCoordinates(scanResult);

        boolean openApiPresent = false;
        try {
            Class.forName(
                    "org.apache.camel.openapi.RestOpenApiReader",
                    false,
                    Thread.currentThread().getContextClassLoader());
            openApiPresent = true;
        } catch (ClassNotFoundException ignored) {
        }

        if (openApiPresent) {
            assertThat(missingCoords).noneMatch(coord -> coord.contains("camel-openapi-java"));
        } else {
            // Since camel-openapi-java is not on the classpath / pom.xml, it must be in the
            // missing
            // coordinates.
            assertThat(missingCoords)
                    .anyMatch(
                            coord ->
                                    coord.contains("camel-openapi-java")
                                            && !coord.contains("starter"));
        }
    }

    @Test
    void testEnsureComponentsAvailable_CircuitBreaker() throws IOException {
        // Read the content of mycb.camel.yaml
        Path yamlPath = Path.of("examples/circuitBreaker/mycb.camel.yaml");
        String yamlContent = Files.readString(yamlPath);

        // Scan the content
        ScanResult scanResult = scanner.scan(yamlContent);

        // Ensure that resilience4jConfiguration (eipHint) is detected
        assertThat(scanResult.eipHints()).contains("resilience4jConfiguration");

        // Resolve missing coordinates
        List<String> missingCoords = mapper.resolveMissingCoordinates(scanResult);

        // Since camel-resilience4j-starter is now in pom.xml, it will NOT be in
        // missingCoords.
        // Let's assert that it's correctly recognized as already present, or downloaded
        // if missing.
        boolean resilience4jPresent = false;
        try {
            Class.forName(
                    "org.apache.camel.component.resilience4j.ResilienceProcessor",
                    false,
                    Thread.currentThread().getContextClassLoader());
            resilience4jPresent = true;
        } catch (ClassNotFoundException ignored) {
        }

        if (resilience4jPresent) {
            assertThat(missingCoords).doesNotContain("org.apache.camel:camel-resilience4j:4.20.0");
        } else {
            assertThat(missingCoords).contains("org.apache.camel:camel-resilience4j:4.20.0");
        }
    }

    @Test
    void testEnsureComponentsAvailable_FaultTolerance() {
        String yamlContent =
                """
                - route:
                    from:
                      uri: timer:yaml
                      steps:
                        - circuitBreaker:
                            faultToleranceConfiguration:
                                delay: 20
                        - log:
                            message: "hello"
                """;

        ScanResult scanResult = scanner.scan(yamlContent);
        assertThat(scanResult.eipHints()).contains("faultToleranceConfiguration");

        List<String> missingCoords = mapper.resolveMissingCoordinates(scanResult);

        // Since camel-microprofile-fault-tolerance is NOT in the pom.xml, it MUST be in
        // the missing
        // coordinates.
        assertThat(missingCoords)
                .anyMatch(
                        coord ->
                                coord.contains("camel-microprofile-fault-tolerance")
                                        && !coord.contains("starter"));
    }

    @Test
    void testEnsureComponentsAvailable_Caffeine() throws IOException {
        // Read the content of camel-caffeine.camel.yaml
        Path yamlPath = Path.of("examples/camel-caffeine/camel-caffeine.camel.yaml");
        String yamlContent = Files.readString(yamlPath);

        // Scan the content to find components
        ScanResult scanResult = scanner.scan(yamlContent);

        // Verify that "caffeine-cache" scheme is parsed successfully
        assertThat(scanResult.schemes()).contains("caffeine-cache");

        // Resolve missing coordinates using the mapper
        List<String> missingCoords = mapper.resolveMissingCoordinates(scanResult);

        // Check if caffeine-cache is already present on the test classpath (e.g. if
        // configured in
        // pom.xml)
        boolean caffeinePresent = false;
        try {
            Class.forName(
                    "org.apache.camel.component.caffeine.cache.CaffeineCacheComponent",
                    false,
                    Thread.currentThread().getContextClassLoader());
            caffeinePresent = true;
        } catch (ClassNotFoundException ignored) {
        }

        if (caffeinePresent) {
            assertThat(missingCoords).doesNotContain("org.apache.camel:camel-caffeine:4.20.0");
        } else {
            assertThat(missingCoords).contains("org.apache.camel:camel-caffeine:4.20.0");
        }
    }

    @Test
    void testScanningClassResolverDirectly() {
        java.util.Set<String> schemes = new java.util.LinkedHashSet<>();
        java.util.Set<String> dataFormats = new java.util.LinkedHashSet<>();
        org.apache.camel.spi.ClassResolver delegate =
                new org.apache.camel.impl.engine.DefaultClassResolver();
        ScanningClassResolver resolver = new ScanningClassResolver(delegate, schemes, dataFormats);

        // Resolve a non-existent Camel dataformat class
        Class<?> clazz =
                resolver.resolveClass("org.apache.camel.dataformat.barcode.BarcodeDataFormat");
        assertThat(clazz).isEqualTo(ScanningClassResolver.DummyDataFormat.class);
        assertThat(dataFormats).contains("barcode");

        // Resolve a non-existent Camel component class
        Class<?> clazz2 =
                resolver.resolveClass(
                        "org.apache.camel.component.caffeine.cache.CaffeineCacheComponent");
        assertThat(clazz2).isEqualTo(ScanningClassResolver.DummyComponent.class);
        assertThat(schemes).contains("caffeine");
    }

    @Test
    void testEnsureComponentsAvailable_Barcode() throws IOException {
        String yamlContent =
                Files.readString(
                        Path.of(
                                "d:/Projects/apache-camel/GitHub/camel-dashboard-examples/Beginner/Barcode/barcode.camel.yaml"));
        ScanResult scanResult = scanner.scan(yamlContent);
        System.out.println("BARCODE SCAN SCHEMES: " + scanResult.schemes());
        System.out.println("BARCODE SCAN DATAFORMATS: " + scanResult.dataFormats());
        System.out.println("BARCODE SCAN LANGUAGES: " + scanResult.languages());
        assertThat(scanResult.dataFormats()).contains("barcode");

        // Verify coordinates mapper resolves the missing dependency for barcode
        List<String> missingCoords = mapper.resolveMissingCoordinates(scanResult);
        System.out.println("BARCODE MISSING COORDS: " + missingCoords);

        // Since camel-barcode is NOT on the classpath, it must be in the missing
        // coordinates.
        assertThat(missingCoords).anyMatch(coord -> coord.contains("camel-barcode"));
    }
}
