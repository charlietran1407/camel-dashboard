package vn.cxn.apache_camel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.cxn.apache_camel.config.RedisClusterProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RedisClusterProperties.class)
public class ApacheCamelApplication {
    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));

        boolean isInitial = false;
        if (args != null) {
            for (String arg : args) {
                if ("--initial".equals(arg)) {
                    isInitial = true;
                    break;
                }
            }
        }

        SpringApplication app = new SpringApplication(ApacheCamelApplication.class);
        if (isInitial) {
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
            System.setProperty("app.initial-mode", "true");
            System.setProperty("route.restore.enabled", "false");
            System.setProperty("camel.dashboard.cluster.enabled", "false");
        }
        app.run(args);
    }
}
