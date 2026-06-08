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
        SpringApplication.run(ApacheCamelApplication.class, args);
    }
}
