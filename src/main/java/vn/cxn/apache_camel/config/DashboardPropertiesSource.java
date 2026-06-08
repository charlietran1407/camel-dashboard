package vn.cxn.apache_camel.config;

import jakarta.annotation.PostConstruct;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.service.EnvPropertyService;

@Component
public class DashboardPropertiesSource implements PropertiesSource {

    private final CamelContext camelContext;
    private final EnvPropertyService envPropertyService;

    public DashboardPropertiesSource(
            CamelContext camelContext, EnvPropertyService envPropertyService) {
        this.camelContext = camelContext;
        this.envPropertyService = envPropertyService;
    }

    @PostConstruct
    public void init() {
        // Register this source with Camel's PropertiesComponent
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        pc.addPropertiesSource(this);
    }

    @Override
    public String getName() {
        return "dashboardPropertiesSource";
    }

    @Override
    public String getProperty(String name) {
        // Camel will call this method whenever it encounters {{name}}
        return envPropertyService.getValue(name);
    }
}
