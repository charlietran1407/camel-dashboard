package vn.cxn.apache_camel.util;

import java.util.*;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.ParamDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.cxn.apache_camel.model.dto.RestParamInfo;

public final class CamelYamlParser {

    private static final Logger log = LoggerFactory.getLogger(CamelYamlParser.class);

    private CamelYamlParser() {
        // Prevent instantiation
    }

    /** Strips metadata block and HikariDataSource beans from YAML route definitions. */
    @SuppressWarnings("unchecked")
    public static String stripMetadata(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("<")) {
            return content;
        }

        try {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Object obj = yaml.load(content);
            if (obj instanceof List) {
                List<Object> newList = new ArrayList<>();
                for (Object element : (List<Object>) obj) {
                    if (element instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) element;
                        if (map.containsKey("metadata")) {
                            continue;
                        }
                        if (map.containsKey("beans")) {
                            Object beansObj = map.get("beans");
                            if (beansObj instanceof List) {
                                List<Object> beansList = new ArrayList<>((List<Object>) beansObj);
                                Iterator<Object> beanIter = beansList.iterator();
                                while (beanIter.hasNext()) {
                                    Object beanEl = beanIter.next();
                                    if (beanEl instanceof Map) {
                                        Map<String, Object> beanMap = (Map<String, Object>) beanEl;
                                        Object typeObj = beanMap.get("type");
                                        if (typeObj != null
                                                && typeObj.toString()
                                                        .contains("HikariDataSource")) {
                                            beanIter.remove();
                                        }
                                    }
                                }
                                if (beansList.isEmpty()) {
                                    continue;
                                } else {
                                    Map<String, Object> newMap = new LinkedHashMap<>(map);
                                    newMap.put("beans", beansList);
                                    newList.add(newMap);
                                }
                            } else {
                                newList.add(element);
                            }
                        } else {
                            newList.add(element);
                        }
                    } else {
                        newList.add(element);
                    }
                }
                return yaml.dump(newList);
            } else if (obj instanceof Map) {
                Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) obj);
                map.remove("metadata");
                if (map.containsKey("beans")) {
                    Object beansObj = map.get("beans");
                    if (beansObj instanceof List) {
                        List<Object> beansList = new ArrayList<>((List<Object>) beansObj);
                        Iterator<Object> beanIter = beansList.iterator();
                        while (beanIter.hasNext()) {
                            Object beanEl = beanIter.next();
                            if (beanEl instanceof Map) {
                                Map<String, Object> beanMap = (Map<String, Object>) beanEl;
                                Object typeObj = beanMap.get("type");
                                if (typeObj != null
                                        && typeObj.toString().contains("HikariDataSource")) {
                                    beanIter.remove();
                                }
                            }
                        }
                        if (beansList.isEmpty()) {
                            map.remove("beans");
                        } else {
                            map.put("beans", beansList);
                        }
                    }
                }
                return yaml.dump(map);
            }
        } catch (Exception e) {
            log.warn("Failed to strip metadata/beans using SnakeYAML: {}", e.getMessage());
        }
        return content;
    }

    public static String stripAllBeansAndMetadata(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("<")) {
            String cleaned = content.replaceAll("(?s)<bean\\b[^>]*>.*?</bean>", "");
            cleaned = cleaned.replaceAll("(?s)<bean\\b[^>]*/>", "");
            return cleaned;
        }

        try {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Object obj = yaml.load(content);
            if (obj instanceof List<?> list) {
                List<Object> newList = new ArrayList<>();
                for (Object element : list) {
                    if (element instanceof Map<?, ?> map) {
                        if (map.containsKey("metadata")) {
                            continue;
                        }
                        if (map.containsKey("beans")) {
                            continue;
                        }
                        newList.add(element);
                    } else {
                        newList.add(element);
                    }
                }
                return yaml.dump(newList);
            } else if (obj instanceof Map<?, ?> map) {
                Map<Object, Object> newMap = new LinkedHashMap<>(map);
                newMap.remove("metadata");
                newMap.remove("beans");
                return yaml.dump(newMap);
            }
        } catch (Exception e) {
            log.warn("Failed to strip metadata and beans using SnakeYAML: {}", e.getMessage());
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    public static String stripNonScriptingBeansAndMetadata(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("<")) {
            return content;
        }

        try {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Object obj = yaml.load(content);
            if (obj instanceof List) {
                List<Object> newList = new ArrayList<>();
                for (Object element : (List<Object>) obj) {
                    if (element instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) element;
                        if (map.containsKey("metadata")) {
                            continue;
                        }
                        if (map.containsKey("beans")) {
                            Object beansObj = map.get("beans");
                            if (beansObj instanceof List) {
                                List<Object> beansList = (List<Object>) beansObj;
                                List<Object> filteredBeans = new ArrayList<>();
                                for (Object beanEl : beansList) {
                                    if (beanEl instanceof Map) {
                                        Map<String, Object> beanMap = (Map<String, Object>) beanEl;
                                        if (beanMap.containsKey("scriptLanguage")
                                                || beanMap.containsKey("script-language")) {
                                            filteredBeans.add(beanEl);
                                        }
                                    }
                                }
                                if (!filteredBeans.isEmpty()) {
                                    Map<String, Object> newMap = new LinkedHashMap<>(map);
                                    newMap.put("beans", filteredBeans);
                                    newList.add(newMap);
                                }
                            }
                        } else {
                            newList.add(element);
                        }
                    } else {
                        newList.add(element);
                    }
                }
                return yaml.dump(newList);
            } else if (obj instanceof Map) {
                Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) obj);
                map.remove("metadata");
                if (map.containsKey("beans")) {
                    Object beansObj = map.get("beans");
                    if (beansObj instanceof List) {
                        List<Object> beansList = (List<Object>) beansObj;
                        List<Object> filteredBeans = new ArrayList<>();
                        for (Object beanEl : beansList) {
                            if (beanEl instanceof Map) {
                                Map<String, Object> beanMap = (Map<String, Object>) beanEl;
                                if (beanMap.containsKey("scriptLanguage")
                                        || beanMap.containsKey("script-language")) {
                                    filteredBeans.add(beanEl);
                                }
                            }
                        }
                        if (filteredBeans.isEmpty()) {
                            map.remove("beans");
                        } else {
                            map.put("beans", filteredBeans);
                        }
                    }
                }
                return yaml.dump(map);
            }
        } catch (Exception e) {
            log.warn("Failed to strip non-scripting beans using SnakeYAML: {}", e.getMessage());
        }
        return content;
    }

    /** Extracts REST paths from a YAML/XML definition using a lightweight CamelContext. */
    public static Set<String> extractRestPathsFromYaml(String content) {
        Set<String> paths = new HashSet<>();
        if (content == null || content.isBlank()) {
            return paths;
        }

        String resourceName = "temp.yaml";
        String trimmed = content.trim();
        if (trimmed.startsWith("<")) {
            resourceName = "temp.xml";
        }

        String cleanedContent = stripAllBeansAndMetadata(content);

        try (DefaultCamelContext tempContext = new DefaultCamelContext()) {
            Resource resource = ResourceHelper.fromString(resourceName, cleanedContent);
            PluginHelper.getRoutesLoader(tempContext).loadRoutes(resource);

            List<RestDefinition> restDefinitions = tempContext.getRestDefinitions();
            if (restDefinitions != null) {
                for (RestDefinition rest : restDefinitions) {
                    if (rest.getPath() != null) {
                        paths.add(rest.getPath());
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to parse DSL for REST paths using native CamelContext: {}",
                    e.getMessage());
        }
        return paths;
    }

    /** Extracts REST definitions from the YAML/XML content using a lightweight CamelContext. */
    public static List<RestDefinition> parseRestDefinitions(String content) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }

        String resourceName = "temp.yaml";
        String trimmed = content.trim();
        if (trimmed.startsWith("<")) {
            resourceName = "temp.xml";
        }

        String cleanedContent = stripAllBeansAndMetadata(content);

        try (DefaultCamelContext tempContext = new DefaultCamelContext()) {
            Resource resource = ResourceHelper.fromString(resourceName, cleanedContent);
            PluginHelper.getRoutesLoader(tempContext).loadRoutes(resource);
            return new ArrayList<>(tempContext.getRestDefinitions());
        } catch (Exception e) {
            log.warn("Failed to parse REST definitions: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Extracts REST parameters matching a specific route from a pre-parsed list of REST
     * definitions.
     */
    public static List<RestParamInfo> extractParamsFromDefinitions(
            List<RestDefinition> restDefinitions, String originalId, String sourceUri) {
        List<RestParamInfo> restParams = new ArrayList<>();
        if (restDefinitions == null) {
            return restParams;
        }

        for (RestDefinition rest : restDefinitions) {
            if (rest.getVerbs() != null) {
                for (VerbDefinition verb : rest.getVerbs()) {
                    String verbRouteId = verb.getRouteId();

                    // 1. Route ID match strategies
                    boolean matches =
                            (originalId != null && originalId.equals(verbRouteId))
                                    || (verbRouteId != null
                                            && originalId != null
                                            && originalId.endsWith("__" + verbRouteId));

                    // 2. HTTP method + path match against sourceUri strategy
                    if (!matches
                            && sourceUri != null
                            && sourceUri.trim().toLowerCase().startsWith("rest:")) {
                        String verbMethod =
                                verb.getClass()
                                        .getSimpleName()
                                        .replace("Definition", "")
                                        .toLowerCase();
                        String restBasePath = rest.getPath() != null ? rest.getPath() : "";
                        String verbSubPath = verb.getPath() != null ? verb.getPath() : "";
                        String combinedPath =
                                ("/" + restBasePath + "/" + verbSubPath)
                                        .replace('\\', '/')
                                        .replaceAll("/+", "/")
                                        .replaceAll("/+$", "");
                        if (combinedPath.isEmpty()) {
                            combinedPath = "/";
                        }
                        String cleanSource =
                                sourceUri
                                        .replaceAll("\\?.*$", "")
                                        .replaceAll("/+$", "")
                                        .toLowerCase();
                        String expected1 =
                                "rest://" + verbMethod + ":" + combinedPath.toLowerCase();
                        String expected2 = "rest:" + verbMethod + ":" + combinedPath.toLowerCase();
                        matches =
                                cleanSource.equals(expected1)
                                        || cleanSource.equals(expected2)
                                        || cleanSource.startsWith(expected1 + "/")
                                        || cleanSource.startsWith(expected2 + "/");
                    }

                    if (matches && verb.getParams() != null) {
                        for (ParamDefinition param : verb.getParams()) {
                            restParams.add(
                                    new RestParamInfo(
                                            param.getName(),
                                            param.getType() != null
                                                    ? param.getType().name().toLowerCase()
                                                    : "query",
                                            param.getDataType() != null
                                                    ? param.getDataType()
                                                    : "string",
                                            param.getRequired() != null
                                                    ? param.getRequired()
                                                    : false,
                                            param.getDescription()));
                        }
                    }
                }
            }
        }
        return restParams;
    }

    /**
     * Parse and extract rest parameters for a specific route from fallback YAML/XML using a
     * lightweight CamelContext.
     */
    public static List<RestParamInfo> parseRestParams(
            String content, String originalId, String sourceUri) {
        try {
            List<RestDefinition> restDefinitions = parseRestDefinitions(content);
            return extractParamsFromDefinitions(restDefinitions, originalId, sourceUri);
        } catch (Exception e) {
            log.warn(
                    "Failed to parse fallback REST parameters of route '{}' using native"
                            + " CamelContext: {}",
                    originalId,
                    e.getMessage());
        }
        return new ArrayList<>();
    }
}
