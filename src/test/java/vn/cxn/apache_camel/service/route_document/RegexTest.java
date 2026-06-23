package vn.cxn.apache_camel.service.route_document;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTest {
    public static void main(String[] args) {
        Pattern pattern = Pattern.compile("^#\\s*camel-dashboard:\\s*dependency\\s*=\\s*(.+)");
        String[] testStrings = {
            "# camel-dashboard:dependency ="
                + " dev.langchain4j:langchain4j-google-ai-gemini:0.31.0,org.apache.camel:camel-langchain4j-embeddings:4.20.0",
            "# camel-dashboard:dependency=dev.langchain4j:langchain4j-google-ai-gemini:1.31.0,org.apache.camel:camel-langchain4j-embeddings:5.20.0",
            "# camel-dashboard:dependency=dev.langchain4j:langchain4j-google-ai-gemini:2.31.0,org.apache.camel:camel-langchain4j-embeddings:6.20.0"
        };
        for (String s : testStrings) {
            Matcher m = pattern.matcher(s);
            System.out.println("String: " + s);
            System.out.println("Matches: " + m.matches());
            if (m.matches()) {
                System.out.println("Group 1: " + m.group(1));
            }
        }
    }
}
