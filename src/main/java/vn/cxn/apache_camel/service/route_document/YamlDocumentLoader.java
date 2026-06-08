package vn.cxn.apache_camel.service.route_document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

final class YamlDocumentLoader {
    private static final Logger log = LoggerFactory.getLogger(YamlDocumentLoader.class);

    private YamlDocumentLoader() {
        // Utility class
    }

    public static List<Object> loadYamlDocuments(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Yaml yaml = new Yaml();
            List<Object> docs = new ArrayList<>();
            yaml.loadAll(content).forEach(docs::add);
            return docs;
        } catch (Exception e) {
            log.warn("Failed to load YAML: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
