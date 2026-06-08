package vn.cxn.apache_camel.service.route_helper;

import org.springframework.stereotype.Service;
import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.service.RouteVersionService;

@Service
public class RouteVersionContentHelperImpl implements RouteVersionContentHelper {

    private final RouteVersionService versionService;

    public RouteVersionContentHelperImpl(RouteVersionService versionService) {
        this.versionService = versionService;
    }

    @Override
    public String normalizeResourceName(String fileName) {
        if (fileName == null) {
            return "";
        }
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".xml")) {
            return fileName;
        }
        return fileName + ".yaml";
    }

    @Override
    public String readDeploymentContent(RouteVersion rv) {
        try {
            return versionService.getDeploymentContent(rv);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot read route content from storage: " + e.getMessage(), e);
        }
    }
}
