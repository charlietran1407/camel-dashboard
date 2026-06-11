package vn.cxn.apache_camel.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.yaml.snakeyaml.Yaml;

public class CamelRouteMermaidParser {

    /** Parses Camel route content (XML or YAML) and returns Mermaid flowchart code. */
    public static String parse(String fileName, String content) {
        if (content == null || content.isBlank()) {
            return "flowchart TD\n  emptyNode[\"No active content available\"]:::step\n"
                    + getStyles();
        }

        try {
            if (fileName != null && fileName.endsWith(".xml")) {
                return parseXml(content);
            } else {
                return parseYaml(content);
            }
        } catch (Exception e) {
            return "flowchart TD\n  errorNode[\"Failed to parse route flow:<br/>"
                    + escapeMermaid(e.getMessage())
                    + "\"]:::error\n"
                    + getStyles();
        }
    }

    /** Parses Camel YAML route content into Mermaid syntax. */
    @SuppressWarnings("unchecked")
    public static String parseYaml(String content) {
        List<String> lines = new ArrayList<>();
        lines.add("flowchart TD");

        Map<String, Integer> counter = new HashMap<>();
        Yaml yaml = new Yaml();

        try {
            Iterable<Object> docs = yaml.loadAll(content);
            boolean foundRoute = false;

            for (Object doc : docs) {
                if (doc instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            if (map.containsKey("route")) {
                                Object routeObj = map.get("route");
                                if (routeObj instanceof Map<?, ?> routeMap) {
                                    parseYamlRoute((Map<String, Object>) routeMap, lines, counter);
                                    foundRoute = true;
                                }
                            } else if (map.containsKey("from")) {
                                parseYamlRoute((Map<String, Object>) map, lines, counter);
                                foundRoute = true;
                            }
                        }
                    }
                } else if (doc instanceof Map<?, ?> map) {
                    if (map.containsKey("route")) {
                        Object routeObj = map.get("route");
                        if (routeObj instanceof Map<?, ?> routeMap) {
                            parseYamlRoute((Map<String, Object>) routeMap, lines, counter);
                            foundRoute = true;
                        }
                    } else if (map.containsKey("from")) {
                        parseYamlRoute((Map<String, Object>) map, lines, counter);
                        foundRoute = true;
                    }
                }
            }

            if (!foundRoute) {
                lines.add("  emptyNode[\"No Camel routes found in YAML\"]:::step");
            }
        } catch (Exception e) {
            lines.add(
                    "  errorNode[\"YAML Parse Error: "
                            + escapeMermaid(e.getMessage())
                            + "\"]:::error");
        }

        lines.add(getStyles());
        return String.join("\n", lines);
    }

    private static void parseYamlRoute(
            Map<String, Object> routeMap, List<String> lines, Map<String, Integer> counter) {
        String routeId = (String) routeMap.get("id");
        String cleanRouteId =
                routeId != null
                        ? routeId.replaceAll("[^A-Za-z0-9_]", "_")
                        : "route_" + nextCount("route", counter);

        lines.add(
                "  subgraph "
                        + cleanRouteId
                        + " [\"Route: "
                        + (routeId != null ? routeId : cleanRouteId)
                        + "\"]");

        Object fromObj = routeMap.get("from");
        if (fromObj instanceof Map<?, ?> fromMap) {
            String uri = (String) fromMap.get("uri");
            String startLabel = "Start: " + (uri != null ? uri : "unknown");

            // Extract endpoint name from parameters if available (e.g. direct:name)
            Object paramsObj = fromMap.get("parameters");
            if (paramsObj instanceof Map<?, ?> params) {
                Object name = params.get("name");
                if (name != null) {
                    startLabel = "Start: " + uri + ":" + name;
                }
            }

            String fromNodeId = cleanRouteId + "_start";
            lines.add("    " + fromNodeId + "([\"" + escapeMermaid(startLabel) + "\"]):::startEnd");

            Object stepsObj = fromMap.get("steps");
            if (stepsObj instanceof List<?> steps) {
                parseYamlSteps(steps, fromNodeId, cleanRouteId, lines, counter);
            }
        } else {
            lines.add(
                    "    "
                            + cleanRouteId
                            + "_empty[\"Empty route (no active start point)\"]:::step");
        }

        lines.add("  end");
    }

    private static String parseYamlSteps(
            List<?> steps,
            String parentNodeId,
            String cleanRouteId,
            List<String> lines,
            Map<String, Integer> counter) {
        String currentParent = parentNodeId;
        for (Object stepObj : steps) {
            if (stepObj instanceof Map<?, ?> stepMap) {
                currentParent = parseYamlStep(stepMap, currentParent, cleanRouteId, lines, counter);
            }
        }
        return currentParent;
    }

    @SuppressWarnings("unchecked")
    private static String parseYamlStep(
            Map<?, ?> stepMap,
            String parentNodeId,
            String cleanRouteId,
            List<String> lines,
            Map<String, Integer> counter) {
        if (stepMap.isEmpty()) return parentNodeId;

        // Get key and value of step
        String stepType = stepMap.keySet().iterator().next().toString();
        Object stepValue = stepMap.get(stepType);

        String stepId = cleanRouteId + "_" + stepType + "_" + nextCount(stepType, counter);

        if ("to".equals(stepType)) {
            String uri = "";
            if (stepValue instanceof Map<?, ?> toMap) {
                uri = Objects.toString(toMap.get("uri"), "");
            } else if (stepValue instanceof String) {
                uri = (String) stepValue;
            }
            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[[\"To: "
                            + escapeMermaid(uri)
                            + "\"]]:::toNode");
            return stepId;

        } else if ("log".equals(stepType)) {
            String message = "";
            if (stepValue instanceof Map<?, ?> logMap) {
                message = Objects.toString(logMap.get("message"), "");
            } else if (stepValue instanceof String) {
                message = (String) stepValue;
            }
            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[\"Log: "
                            + escapeMermaid(message)
                            + "\"]:::step");
            return stepId;

        } else if ("setBody".equals(stepType)) {
            String expression = "Set Body";
            if (stepValue instanceof Map<?, ?> bodyMap) {
                for (Map.Entry<?, ?> entry : bodyMap.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> expMap) {
                        expression =
                                "Set Body: "
                                        + entry.getKey()
                                        + " ("
                                        + Objects.toString(expMap.get("expression"), "")
                                        + ")";
                        break;
                    } else if (entry.getValue() instanceof String) {
                        expression = "Set Body: " + entry.getKey() + " (" + entry.getValue() + ")";
                        break;
                    }
                }
            }
            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[\""
                            + escapeMermaid(expression)
                            + "\"]:::step");
            return stepId;

        } else if ("choice".equals(stepType) && stepValue instanceof Map<?, ?> choiceMap) {
            String choiceNodeId = cleanRouteId + "_choice_" + nextCount("choice_diamond", counter);
            String joinNodeId = cleanRouteId + "_choice_end_" + nextCount("choice_join", counter);

            lines.add("    " + parentNodeId + " --> " + choiceNodeId + "{\"Choice\"}:::choice");
            lines.add("    " + joinNodeId + "([\" \"]):::joinNode");

            // Process when branches
            Object whenObj = choiceMap.get("when");
            if (whenObj instanceof List<?> whens) {
                for (int i = 0; i < whens.size(); i++) {
                    Object wObj = whens.get(i);
                    if (wObj instanceof Map<?, ?> wMap) {
                        String whenLabel = "when [" + (i + 1) + "]";
                        // Extract expression name
                        for (Map.Entry<?, ?> entry : wMap.entrySet()) {
                            if (!"steps".equals(entry.getKey())) {
                                if (entry.getValue() instanceof Map<?, ?> expMap) {
                                    whenLabel =
                                            "when: "
                                                    + entry.getKey()
                                                    + " ("
                                                    + Objects.toString(expMap.get("expression"), "")
                                                    + ")";
                                } else if (entry.getValue() instanceof String) {
                                    whenLabel =
                                            "when: "
                                                    + entry.getKey()
                                                    + " ("
                                                    + entry.getValue()
                                                    + ")";
                                }
                                break;
                            }
                        }

                        String whenStartId =
                                cleanRouteId + "_when_start_" + nextCount("when_branch", counter);
                        lines.add(
                                "    "
                                        + choiceNodeId
                                        + " --> |\""
                                        + escapeMermaid(whenLabel)
                                        + "\"| "
                                        + whenStartId
                                        + "[\"Branch Start\"]:::step");

                        Object whenSteps = wMap.get("steps");
                        String lastBranchNode = whenStartId;
                        if (whenSteps instanceof List<?> bSteps) {
                            lastBranchNode =
                                    parseYamlSteps(
                                            bSteps, whenStartId, cleanRouteId, lines, counter);
                        }
                        lines.add("    " + lastBranchNode + " --> " + joinNodeId);
                    }
                }
            }

            // Process otherwise branch
            Object otherwiseObj = choiceMap.get("otherwise");
            if (otherwiseObj instanceof Map<?, ?> otherwiseMap) {
                String otherwiseStartId =
                        cleanRouteId + "_otherwise_start_" + nextCount("otherwise_branch", counter);
                lines.add(
                        "    "
                                + choiceNodeId
                                + " --> |\"otherwise\"| "
                                + otherwiseStartId
                                + "[\"Otherwise\"]:::step");

                Object otherwiseSteps = otherwiseMap.get("steps");
                String lastBranchNode = otherwiseStartId;
                if (otherwiseSteps instanceof List<?> bSteps) {
                    lastBranchNode =
                            parseYamlSteps(bSteps, otherwiseStartId, cleanRouteId, lines, counter);
                }
                lines.add("    " + lastBranchNode + " --> " + joinNodeId);
            } else {
                lines.add("    " + choiceNodeId + " --> |\"otherwise\"| " + joinNodeId);
            }

            return joinNodeId;

        } else if ("saga".equals(stepType) && stepValue instanceof Map<?, ?> sagaMap) {
            String sagaId = Objects.toString(sagaMap.get("id"), "Saga");
            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[\"Saga: "
                            + escapeMermaid(sagaId)
                            + "\"]:::step");

            Object compObj = sagaMap.get("compensation");
            if (compObj instanceof Map<?, ?> compMap) {
                String uri = Objects.toString(compMap.get("uri"), "");
                if (!uri.isEmpty()) {
                    String compNodeId = stepId + "_comp";
                    lines.add(
                            "    "
                                    + compNodeId
                                    + "[[\"Compensate: "
                                    + escapeMermaid(uri)
                                    + "\"]]:::toNode");
                    lines.add("    " + stepId + " -.-> |\"compensate\"| " + compNodeId);
                }
            }

            Object compModeObj = sagaMap.get("completion");
            if (compModeObj instanceof Map<?, ?> compModeMap) {
                String uri = Objects.toString(compModeMap.get("uri"), "");
                if (!uri.isEmpty()) {
                    String complNodeId = stepId + "_compl";
                    lines.add(
                            "    "
                                    + complNodeId
                                    + "[[\"Complete: "
                                    + escapeMermaid(uri)
                                    + "\"]]:::toNode");
                    lines.add("    " + stepId + " -.-> |\"complete\"| " + complNodeId);
                }
            }

            Object stepsObj = sagaMap.get("steps");
            if (stepsObj instanceof List<?> steps) {
                return parseYamlSteps(steps, stepId, cleanRouteId, lines, counter);
            }
            return stepId;

        } else {
            // General step (process, bean, split, filter, marshal, unmarshal, delay, etc.)
            String label = stepType;
            if (stepValue instanceof Map<?, ?> stepDetails) {
                if (stepDetails.containsKey("ref")) {
                    label += ": ref=" + stepDetails.get("ref");
                }
                if (stepDetails.containsKey("expression")) {
                    label += " (" + stepDetails.get("expression") + ")";
                }
            }
            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[\""
                            + escapeMermaid(label)
                            + "\"]:::step");

            // Handle nested steps recursively (e.g. filter, split)
            if (stepValue instanceof Map<?, ?> stepDetails && stepDetails.containsKey("steps")) {
                Object subStepsObj = stepDetails.get("steps");
                if (subStepsObj instanceof List<?> subSteps) {
                    return parseYamlSteps(subSteps, stepId, cleanRouteId, lines, counter);
                }
            }
            return stepId;
        }
    }

    /** Parses Camel XML route content into Mermaid syntax. */
    public static String parseXml(String content) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("flowchart TD");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc =
                builder.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        Map<String, Integer> counter = new HashMap<>();
        NodeList routes = doc.getElementsByTagName("route");
        boolean foundRoute = false;

        for (int i = 0; i < routes.getLength(); i++) {
            Element route = (Element) routes.item(i);
            parseXmlRoute(route, lines, counter);
            foundRoute = true;
        }

        if (!foundRoute) {
            lines.add("  emptyNode[\"No Camel routes found in XML\"]:::step");
        }

        lines.add(getStyles());
        return String.join("\n", lines);
    }

    private static void parseXmlRoute(
            Element route, List<String> lines, Map<String, Integer> counter) {
        String routeId = route.getAttribute("id");
        String cleanRouteId =
                routeId != null && !routeId.isBlank()
                        ? routeId.replaceAll("[^A-Za-z0-9_]", "_")
                        : "route_" + nextCount("route", counter);

        lines.add(
                "  subgraph "
                        + cleanRouteId
                        + " [\"Route: "
                        + (routeId != null && !routeId.isBlank() ? routeId : cleanRouteId)
                        + "\"]");

        NodeList froms = route.getElementsByTagName("from");
        if (froms.getLength() > 0) {
            Element from = (Element) froms.item(0);
            String uri = from.getAttribute("uri");
            String startLabel = "Start: " + (uri != null && !uri.isEmpty() ? uri : "unknown");

            String fromNodeId = cleanRouteId + "_start";
            lines.add("    " + fromNodeId + "([\"" + escapeMermaid(startLabel) + "\"]):::startEnd");

            // Get all siblings of the <from> tag as steps inside the route
            List<Element> steps = new ArrayList<>();
            Node nextSibling = from.getNextSibling();
            while (nextSibling != null) {
                if (nextSibling instanceof Element siblingEl) {
                    if (!"description".equalsIgnoreCase(siblingEl.getTagName())) {
                        steps.add(siblingEl);
                    }
                }
                nextSibling = nextSibling.getNextSibling();
            }

            parseXmlSteps(steps, fromNodeId, cleanRouteId, lines, counter);
        } else {
            lines.add(
                    "    "
                            + cleanRouteId
                            + "_empty[\"Empty XML Route (no `<from>` element)\"]:::step");
        }

        lines.add("  end");
    }

    private static String parseXmlSteps(
            List<Element> steps,
            String parentNodeId,
            String cleanRouteId,
            List<String> lines,
            Map<String, Integer> counter) {
        String currentParent = parentNodeId;
        for (Element step : steps) {
            currentParent = parseXmlStep(step, currentParent, cleanRouteId, lines, counter);
        }
        return currentParent;
    }

    private static String parseXmlStep(
            Element step,
            String parentNodeId,
            String cleanRouteId,
            List<String> lines,
            Map<String, Integer> counter) {
        String stepType = step.getTagName();
        String stepId = cleanRouteId + "_" + stepType + "_" + nextCount(stepType, counter);

        if ("to".equals(stepType)) {
            String uri = step.getAttribute("uri");
            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[[\"To: "
                            + escapeMermaid(uri)
                            + "\"]]:::toNode");
            return stepId;

        } else if ("log".equals(stepType)) {
            String message = step.getAttribute("message");
            if (message == null || message.isEmpty()) {
                message = step.getTextContent();
            }
            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[\"Log: "
                            + escapeMermaid(message)
                            + "\"]:::step");
            return stepId;

        } else if ("setBody".equals(stepType)) {
            String label = "Set Body";
            // Check for simple, groovy, constant, etc.
            NodeList children = step.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element expEl) {
                    label =
                            "Set Body: "
                                    + expEl.getTagName()
                                    + " ("
                                    + expEl.getTextContent().trim()
                                    + ")";
                    break;
                }
            }
            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[\""
                            + escapeMermaid(label)
                            + "\"]:::step");
            return stepId;

        } else if ("choice".equals(stepType)) {
            String choiceNodeId = cleanRouteId + "_choice_" + nextCount("choice_diamond", counter);
            String joinNodeId = cleanRouteId + "_choice_end_" + nextCount("choice_join", counter);

            lines.add("    " + parentNodeId + " --> " + choiceNodeId + "{\"Choice\"}:::choice");
            lines.add("    " + joinNodeId + "([\" \"]):::joinNode");

            // Process when branches
            NodeList whens = step.getElementsByTagName("when");
            for (int i = 0; i < whens.getLength(); i++) {
                Element when = (Element) whens.item(i);

                // Try to find expression element (e.g. <simple>, <groovy>)
                String whenLabel = "when [" + (i + 1) + "]";
                NodeList whenChildren = when.getChildNodes();
                for (int j = 0; j < whenChildren.getLength(); j++) {
                    if (whenChildren.item(j) instanceof Element expEl
                            && !"steps".equalsIgnoreCase(expEl.getTagName())) {
                        String tagName = expEl.getTagName();
                        String expContent = expEl.getTextContent().trim();
                        if (!expContent.isEmpty()) {
                            whenLabel = "when: " + tagName + " (" + expContent + ")";
                            break;
                        }
                    }
                }

                String whenStartId =
                        cleanRouteId + "_when_start_" + nextCount("when_branch", counter);
                lines.add(
                        "    "
                                + choiceNodeId
                                + " --> |\""
                                + escapeMermaid(whenLabel)
                                + "\"| "
                                + whenStartId
                                + "[\"Branch Start\"]:::step");

                // Get steps within <when>
                List<Element> whenSteps = getDirectChildElements(when);
                // Remove expression tag
                whenSteps.removeIf(
                        el ->
                                !"to".equals(el.getTagName())
                                        && !"log".equals(el.getTagName())
                                        && !"setBody".equals(el.getTagName())
                                        && !"choice".equals(el.getTagName())
                                        && !"saga".equals(el.getTagName())
                                        && !"process".equals(el.getTagName())
                                        && !"bean".equals(el.getTagName())
                                        && !"split".equals(el.getTagName())
                                        && !"filter".equals(el.getTagName())
                                        && !"marshal".equals(el.getTagName())
                                        && !"unmarshal".equals(el.getTagName())
                                        && !"delay".equals(el.getTagName()));

                String lastBranchNode = whenStartId;
                if (!whenSteps.isEmpty()) {
                    lastBranchNode =
                            parseXmlSteps(whenSteps, whenStartId, cleanRouteId, lines, counter);
                }
                lines.add("    " + lastBranchNode + " --> " + joinNodeId);
            }

            // Process otherwise branch
            NodeList otherwises = step.getElementsByTagName("otherwise");
            if (otherwises.getLength() > 0) {
                Element otherwise = (Element) otherwises.item(0);
                String otherwiseStartId =
                        cleanRouteId + "_otherwise_start_" + nextCount("otherwise_branch", counter);
                lines.add(
                        "    "
                                + choiceNodeId
                                + " --> |\"otherwise\"| "
                                + otherwiseStartId
                                + "[\"Otherwise\"]:::step");

                List<Element> otherwiseSteps = getDirectChildElements(otherwise);
                String lastBranchNode = otherwiseStartId;
                if (!otherwiseSteps.isEmpty()) {
                    lastBranchNode =
                            parseXmlSteps(
                                    otherwiseSteps, otherwiseStartId, cleanRouteId, lines, counter);
                }
                lines.add("    " + lastBranchNode + " --> " + joinNodeId);
            } else {
                lines.add("    " + choiceNodeId + " --> |\"otherwise\"| " + joinNodeId);
            }

            return joinNodeId;

        } else if ("saga".equals(stepType)) {
            String sagaId = step.getAttribute("id");
            if (sagaId == null || sagaId.isEmpty()) sagaId = "Saga";

            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[\"Saga: "
                            + escapeMermaid(sagaId)
                            + "\"]:::step");

            NodeList compList = step.getElementsByTagName("compensation");
            if (compList.getLength() > 0) {
                Element comp = (Element) compList.item(0);
                String uri = comp.getAttribute("uri");
                if (!uri.isEmpty()) {
                    String compNodeId = stepId + "_comp";
                    lines.add(
                            "    "
                                    + compNodeId
                                    + "[[\"Compensate: "
                                    + escapeMermaid(uri)
                                    + "\"]]:::toNode");
                    lines.add("    " + stepId + " -.-> |\"compensate\"| " + compNodeId);
                }
            }

            NodeList complList = step.getElementsByTagName("completion");
            if (complList.getLength() > 0) {
                Element compl = (Element) complList.item(0);
                String uri = compl.getAttribute("uri");
                if (!uri.isEmpty()) {
                    String complNodeId = stepId + "_compl";
                    lines.add(
                            "    "
                                    + complNodeId
                                    + "[[\"Complete: "
                                    + escapeMermaid(uri)
                                    + "\"]]:::toNode");
                    lines.add("    " + stepId + " -.-> |\"complete\"| " + complNodeId);
                }
            }

            List<Element> sagaSteps = getDirectChildElements(step);
            // filter out metadata nodes
            sagaSteps.removeIf(
                    el ->
                            "compensation".equalsIgnoreCase(el.getTagName())
                                    || "completion".equalsIgnoreCase(el.getTagName()));

            if (!sagaSteps.isEmpty()) {
                return parseXmlSteps(sagaSteps, stepId, cleanRouteId, lines, counter);
            }
            return stepId;

        } else {
            // General EIP
            String label = stepType;
            String ref = step.getAttribute("ref");
            if (ref != null && !ref.isEmpty()) {
                label += ": ref=" + ref;
            }

            lines.add(
                    "    "
                            + parentNodeId
                            + " --> "
                            + stepId
                            + "[\""
                            + escapeMermaid(label)
                            + "\"]:::step");

            // Handle nested nodes recursively
            List<Element> subSteps = getDirectChildElements(step);
            if (!subSteps.isEmpty()) {
                return parseXmlSteps(subSteps, stepId, cleanRouteId, lines, counter);
            }
            return stepId;
        }
    }

    private static List<Element> getDirectChildElements(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodeList = parent.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i) instanceof Element el) {
                children.add(el);
            }
        }
        return children;
    }

    private static int nextCount(String type, Map<String, Integer> counter) {
        return counter.compute(type, (k, v) -> v == null ? 1 : v + 1);
    }

    private static String escapeMermaid(String s) {
        if (s == null) return "";
        return s.replace("\"", "'")
                .replace("\r", "")
                .replace("\n", "<br/>")
                .replace("[", "&#91;")
                .replace("]", "&#93;")
                .replace("(", "&#40;")
                .replace(")", "&#41;")
                .replace("{", "&#123;")
                .replace("}", "&#125;");
    }

    private static String getStyles() {
        return "\n"
                + "  %% Custom Styles matching modern dashboard dark mode\n"
                + "  classDef startEnd"
                + " fill:#6366f1,stroke:#4f46e5,stroke-width:2px,color:#ffffff;\n"
                + "  classDef step fill:#1a1d27,stroke:#374151,stroke-width:1px,color:#e2e8f0;\n"
                + "  classDef choice"
                + " fill:#f59e0b,stroke:#d97706,stroke-width:1px,color:#ffffff;\n"
                + "  classDef toNode"
                + " fill:#059669,stroke:#047857,stroke-width:2px,color:#ffffff;\n"
                + "  classDef joinNode"
                + " fill:#4b5563,stroke:#374151,stroke-width:1px,color:#e2e8f0;\n"
                + "  classDef error"
                + " fill:#ef4444,stroke:#dc2626,stroke-width:2px,color:#ffffff;\n";
    }
}
