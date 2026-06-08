package vn.cxn.apache_camel.service.route_helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.camel.CamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.dto.RestParamInfo;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.entity.RouteEntity;
import vn.cxn.apache_camel.repository.RouteRepository;
import vn.cxn.apache_camel.service.RouteVersionService;
import vn.cxn.apache_camel.util.CamelYamlParser;

@Service
public class RestParamExtractorImpl implements RestParamExtractor {

    private static final Logger log = LoggerFactory.getLogger(RestParamExtractorImpl.class);

    private final CamelContext camelContext;
    private final RouteRepository routeRepository;
    private final RouteVersionService versionService;

    public RestParamExtractorImpl(
            CamelContext camelContext,
            RouteRepository routeRepository,
            RouteVersionService versionService) {
        this.camelContext = camelContext;
        this.routeRepository = routeRepository;
        this.versionService = versionService;
    }

    @Override
    public List<RestParamInfo> extractRestParams(String id, String originalId, String sourceUri) {
        List<RestParamInfo> restParams = new ArrayList<>();

        if (!(camelContext instanceof ModelCamelContext modelContext)) {
            return restParams;
        }

        var restDefinitions = modelContext.getRestDefinitions();
        if (restDefinitions == null) {
            return restParams;
        }

        try {
            for (var rest : restDefinitions) {
                if (rest.getVerbs() == null) {
                    continue;
                }

                for (var verb : rest.getVerbs()) {
                    if (isRouteMatchingVerb(id, originalId, sourceUri, verb, rest)) {
                        addVerbParameters(restParams, verb);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to extract REST parameters from Camel runtime for route '{}': {}",
                    id,
                    e.getMessage());
        }
        return restParams;
    }

    @Override
    public List<RestParamInfo> extractFallbackRestParams(
            String id, String originalId, String sourceUri) {
        List<RestParamInfo> restParams = new ArrayList<>();
        String activeVersionId = null;
        Optional<RouteEntity> routeEntityOpt = routeRepository.findById(id);
        if (routeEntityOpt.isPresent() && routeEntityOpt.get().getVersion() != null) {
            activeVersionId = routeEntityOpt.get().getVersion().getId().toString();
        } else {
            List<RouteVersion> routeVersions = versionService.getVersionsByRouteId(id);
            Optional<RouteVersion> activeVersionOpt =
                    routeVersions.stream().filter(RouteVersion::isAutoRestore).findFirst();
            if (activeVersionOpt.isPresent()) {
                activeVersionId = activeVersionOpt.get().getId();
            }
        }

        if (activeVersionId != null) {
            log.info(
                    "Starting fallback REST parsing for route '{}' with version '{}'",
                    id,
                    activeVersionId);
            try {
                String yamlContent = versionService.getContentFromDisk(activeVersionId);
                if (yamlContent != null && !yamlContent.isBlank()) {
                    log.info("Read YAML content from disk, size: {} chars", yamlContent.length());
                    return CamelYamlParser.parseRestParams(yamlContent, originalId, sourceUri);
                } else {
                    log.warn(
                            "YAML content from disk was null or blank for version '{}'",
                            activeVersionId);
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to parse YAML fallback for REST parameters of route '{}': {}",
                        id,
                        e.getMessage(),
                        e);
            }
            log.info(
                    "Completed fallback REST parsing for route '{}', extracted params count: {}",
                    id,
                    restParams.size());
        }
        return restParams;
    }

    @Override
    public boolean isRestSourceUriMatching(
            String sourceUri, String verbMethod, String combinedPath) {
        if (sourceUri == null) {
            return false;
        }
        String trimUri = sourceUri.trim().toLowerCase();
        if (!trimUri.startsWith("rest:")
                && !trimUri.startsWith("rest-api:")
                && !trimUri.startsWith("rest-openapi:")) {
            return false;
        }
        ParsedRestUri parsed = parseRestUri(sourceUri);
        if (parsed == null) {
            return false;
        }
        if (!parsed.method.equalsIgnoreCase(verbMethod)) {
            return false;
        }
        String normRoutePath = normalizeRestPathForComparison(parsed.path);
        String normVerbPath = normalizeRestPathForComparison(combinedPath);
        return normRoutePath.equals(normVerbPath);
    }

    @Override
    public String normalizeRestPathForComparison(String path) {
        if (path == null) {
            return "";
        }
        String decoded = path;
        try {
            decoded =
                    java.net.URLDecoder.decode(
                            path, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
        }
        return decoded.replace(":/", "/")
                .replace('\\', '/')
                .replaceAll("/+", "/")
                .replaceAll("/+$", "")
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public ParsedRestUri parseRestUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        try {
            String cleanUri = uri.trim();
            String queryStr = "";
            int qIdx = cleanUri.indexOf('?');
            if (qIdx >= 0) {
                queryStr = cleanUri.substring(qIdx + 1);
                cleanUri = cleanUri.substring(0, qIdx);
            }

            ParsedRestUri parsed = new ParsedRestUri();
            if (cleanUri.startsWith("rest-api://") || cleanUri.startsWith("rest-api:")) {
                parsed.method = "GET";
                String path =
                        cleanUri.startsWith("rest-api://")
                                ? cleanUri.substring(11)
                                : cleanUri.substring(9);
                parsed.path =
                        ("/" + path).replace('\\', '/').replaceAll("/+", "/").replaceAll("/+$", "");
                if (parsed.path.isEmpty()) {
                    parsed.path = "/";
                }
            } else if (cleanUri.startsWith("rest-openapi://")
                    || cleanUri.startsWith("rest-openapi:")) {
                parsed.method = "GET";
                String path =
                        cleanUri.startsWith("rest-openapi://")
                                ? cleanUri.substring(15)
                                : cleanUri.substring(13);
                parsed.path =
                        ("/" + path).replace('\\', '/').replaceAll("/+", "/").replaceAll("/+$", "");
                if (parsed.path.isEmpty()) {
                    parsed.path = "/";
                }
            } else {
                String sub = null;
                if (cleanUri.startsWith("rest://")) {
                    sub = cleanUri.substring(7);
                } else if (cleanUri.startsWith("rest:")) {
                    sub = cleanUri.substring(5);
                }
                if (sub == null) {
                    return null;
                }

                int colonIdx = sub.indexOf(':');
                if (colonIdx < 0) {
                    return null;
                }

                parsed.method = sub.substring(0, colonIdx).toUpperCase();
                String rawPath = sub.substring(colonIdx + 1);
                if (!rawPath.startsWith("/")) {
                    rawPath = "/" + rawPath;
                }
                parsed.path =
                        rawPath.replace('\\', '/').replaceAll("/+", "/").replaceAll("/+$", "");
                if (parsed.path.isEmpty()) {
                    parsed.path = "/";
                }
            }

            if (!queryStr.isEmpty()) {
                String[] pairs = queryStr.split("&");
                for (String pair : pairs) {
                    int eqIdx = pair.indexOf('=');
                    if (eqIdx > 0) {
                        String key = pair.substring(0, eqIdx).trim();
                        String value = pair.substring(eqIdx + 1).trim();
                        try {
                            value =
                                    java.net.URLDecoder.decode(
                                            value, java.nio.charset.StandardCharsets.UTF_8.name());
                        } catch (Exception ignored) {
                        }
                        if ("consumes".equalsIgnoreCase(key)) {
                            parsed.consumes = value;
                        } else if ("produces".equalsIgnoreCase(key)) {
                            parsed.produces = value;
                        }
                    }
                }
            }
            if (parsed.path != null) {
                String decodedPath = parsed.path;
                try {
                    decodedPath =
                            java.net.URLDecoder.decode(
                                    parsed.path, java.nio.charset.StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {
                }
                parsed.path =
                        decodedPath
                                .replace(":/", "/")
                                .replace('\\', '/')
                                .replaceAll("/+", "/")
                                .replaceAll("/+$", "");
                if (parsed.path.isEmpty()) {
                    parsed.path = "/";
                }
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse REST URI '{}': {}", uri, e.getMessage());
            return null;
        }
    }

    private boolean isRouteMatchingVerb(
            String id,
            String originalId,
            String sourceUri,
            VerbDefinition verb,
            RestDefinition rest) {
        var verbRouteId = verb.getRouteId();

        // 1. Kiểm tra khớp trực tiếp theo ID
        if (id != null
                && (id.equals(verbRouteId)
                        || (verbRouteId != null && id.endsWith("__" + verbRouteId)))) {
            return true;
        }
        if (originalId != null && originalId.equals(verbRouteId)) {
            return true;
        }

        // 2. Fallback: Kiểm tra khớp theo REST path + HTTP method chống lại sourceUri
        var verbMethod = verb.getClass().getSimpleName().replace("Definition", "").toLowerCase();
        var restBasePath = rest.getPath() != null ? rest.getPath() : "";
        var verbSubPath = verb.getPath() != null ? verb.getPath() : "";

        var combinedPath =
                ("/" + restBasePath + "/" + verbSubPath)
                        .replace('\\', '/')
                        .replaceAll("/+", "/")
                        .replaceAll("/+$", "");

        if (combinedPath.isEmpty()) {
            combinedPath = "/";
        }

        return isRestSourceUriMatching(sourceUri, verbMethod, combinedPath);
    }

    private void addVerbParameters(List<RestParamInfo> restParams, VerbDefinition verb) {
        if (verb.getParams() == null) return;

        for (var param : verb.getParams()) {
            var type = param.getType() != null ? param.getType().name().toLowerCase() : "query";
            var dataType = param.getDataType() != null ? param.getDataType() : "string";
            var required = param.getRequired() != null ? param.getRequired() : false;

            restParams.add(
                    new RestParamInfo(
                            param.getName(), type, dataType, required, param.getDescription()));
        }
    }
}
