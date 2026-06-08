package vn.cxn.apache_camel.service.component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.service.component.CamelYamlComponentScanner.ScanResult;

/**
 * Maps Camel component schemes, EIP hints, data format names, and language names to Maven
 * coordinates using the official {@link CamelCatalog}. This is the same source Camel JBang uses —
 * no manual coordinate maintenance needed.
 *
 * <p>Only coordinates that are <em>not</em> already present on the JVM classpath are returned.
 *
 * <h2>What each type resolves to</h2>
 *
 * <ul>
 *   <li><b>Component scheme</b> (e.g. {@code aws2-s3}) → {@code
 *       org.apache.camel:camel-aws2-s3:VERSION} via {@link CamelCatalog#componentModel}
 *   <li><b>EIP hint</b> (e.g. {@code resilience4jConfiguration}) → explicitly mapped to {@code
 *       org.apache.camel:camel-resilience4j:VERSION}
 *   <li><b>Data format</b> (e.g. {@code avro}) → {@code org.apache.camel:camel-avro:VERSION} via
 *       {@link CamelCatalog#dataFormatModel}
 *   <li><b>Language</b> (e.g. {@code jq}) → {@code org.apache.camel:camel-jq:VERSION} via {@link
 *       CamelCatalog#languageModel}
 * </ul>
 */
@Service
public class CamelCatalogCoordinateMapper {

    private static final Logger log = LoggerFactory.getLogger(CamelCatalogCoordinateMapper.class);

    private final CamelCatalog catalog;

    public CamelCatalogCoordinateMapper() {
        this.catalog = new DefaultCamelCatalog(true); // true = caches lookups
    }

    /**
     * Resolve Maven coordinates for all detected artefacts that are missing from the current
     * classpath.
     *
     * @return list of {@code groupId:artifactId:version} strings ready for Maven resolution
     */
    public List<String> resolveMissingCoordinates(ScanResult scanResult) {
        Set<String> coords = new LinkedHashSet<>();

        for (String scheme : scanResult.schemes()) {
            if (isComponentMissing(scheme)) {
                String coord = resolveComponentCoord(scheme);
                if (coord != null) coords.add(coord);
            }
        }

        for (String hint : scanResult.eipHints()) {
            if (isEipHintMissing(hint)) {
                String coord = resolveEipHintCoord(hint);
                if (coord != null) coords.add(coord);
            }
        }

        for (String format : scanResult.dataFormats()) {
            if (isDataFormatMissing(format)) {
                String coord = resolveDataFormatCoord(format);
                if (coord != null) coords.add(coord);
            }
        }

        for (String language : scanResult.languages()) {
            if (isLanguageMissing(language)) {
                String coord = resolveLanguageCoord(language);
                if (coord != null) coords.add(coord);
            }
        }

        for (String extraDep : scanResult.extraDependencies()) {
            if (isExtraDependencyMissing(extraDep)) {
                String coord = resolveOtherCoord(extraDep);
                if (coord != null) coords.add(coord);
            }
        }

        return new ArrayList<>(coords);
    }

    // -------------------------------------------------------------------------
    // Per-type resolution helpers
    // -------------------------------------------------------------------------

    private String resolveComponentCoord(String scheme) {
        try {
            ComponentModel model = catalog.componentModel(scheme);
            return gavFrom(scheme, "component", model);
        } catch (Exception e) {
            log.warn("Could not resolve component coord for '{}': {}", scheme, e.getMessage());
            return null;
        }
    }

    private String resolveDataFormatCoord(String name) {
        try {
            DataFormatModel model = catalog.dataFormatModel(name);
            return gavFrom(name, "dataformat", model);
        } catch (Exception e) {
            log.warn("Could not resolve data-format coord for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    private String resolveLanguageCoord(String name) {
        try {
            LanguageModel model = catalog.languageModel(name);
            return gavFrom(name, "language", model);
        } catch (Exception e) {
            log.warn("Could not resolve language coord for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * EIP implementation hints are not in the component catalog by name — they are mapped
     * explicitly because they represent a choice between alternative backend JARs.
     */
    private String resolveEipHintCoord(String hint) {
        return switch (hint) {
            case "resilience4jConfiguration", "resilience4j-configuration" ->
                    "org.apache.camel:camel-resilience4j:" + catalog.getCatalogVersion();
            case "faultToleranceConfiguration", "fault-tolerance-configuration" ->
                    "org.apache.camel:camel-microprofile-fault-tolerance:"
                            + catalog.getCatalogVersion();
            default -> {
                log.debug("No coordinate mapping for EIP hint '{}'", hint);
                yield null;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Classpath existence checking via CamelCatalog java types
    // -------------------------------------------------------------------------

    private boolean isDependencyMissing(ArtifactModel<?> model) {
        if (model == null) {
            return true;
        }
        String groupId = model.getGroupId();
        String artifactId = model.getArtifactId();
        if (groupId == null || artifactId == null || groupId.isBlank() || artifactId.isBlank()) {
            return true;
        }
        String resourcePath = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        return Thread.currentThread().getContextClassLoader().getResource(resourcePath) == null;
    }

    private boolean isComponentMissing(String scheme) {
        ComponentModel model = catalog.componentModel(scheme);
        return isDependencyMissing(model);
    }

    private boolean isDataFormatMissing(String name) {
        DataFormatModel model = catalog.dataFormatModel(name);
        return isDependencyMissing(model);
    }

    private boolean isLanguageMissing(String name) {
        LanguageModel model = catalog.languageModel(name);
        return isDependencyMissing(model);
    }

    private boolean isEipHintMissing(String hint) {
        String artifactId =
                switch (hint) {
                    case "resilience4jConfiguration", "resilience4j-configuration" ->
                            "camel-resilience4j";
                    case "faultToleranceConfiguration", "fault-tolerance-configuration" ->
                            "camel-microprofile-fault-tolerance";
                    default -> null;
                };
        if (artifactId == null) {
            return false;
        }
        String resourcePath = "META-INF/maven/org.apache.camel/" + artifactId + "/pom.properties";
        return Thread.currentThread().getContextClassLoader().getResource(resourcePath) == null;
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private boolean isExtraDependencyMissing(String name) {
        org.apache.camel.tooling.model.OtherModel model = catalog.otherModel(name);
        return isDependencyMissing(model);
    }

    private String resolveOtherCoord(String name) {
        try {
            org.apache.camel.tooling.model.OtherModel model = catalog.otherModel(name);
            return gavFrom(name, "other", model);
        } catch (Exception e) {
            log.warn("Could not resolve other coord for '{}': {}", name, e.getMessage());
            return "org.apache.camel:camel-" + name + ":" + catalog.getCatalogVersion();
        }
    }

    /**
     * Build a {@code groupId:artifactId:version} string from any {@link ArtifactModel} (component,
     * data-format, or language). Returns {@code null} if the model is {@code null} or has no GAV.
     */
    private String gavFrom(String name, String kind, ArtifactModel<?> model) {
        if (model == null) {
            log.debug("Camel Catalog has no {} entry for '{}'", kind, name);
            return null;
        }
        String groupId = model.getGroupId();
        String artifactId = model.getArtifactId();
        String version = model.getVersion();
        if (groupId == null || artifactId == null) {
            log.debug("Catalog {} entry for '{}' has no GAV", kind, name);
            return null;
        }
        if (version == null || version.isBlank()) {
            version = catalog.getCatalogVersion();
        }
        String coord = groupId + ":" + artifactId + ":" + version;
        log.debug("Resolved {} '{}' → {}", kind, name, coord);
        return coord;
    }
}
