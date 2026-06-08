package vn.cxn.apache_camel.controller;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.springframework.web.bind.annotation.*;

/**
 * @deprecated Use RouteManagementController and RouteVersionController instead. Kept here only as a
 *     legacy stub to avoid breaking existing integrations.
 */
@RestController
@RequestMapping("/api/legacy-routes")
public class RouteController {

    private final CamelContext camelContext;

    public RouteController(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @GetMapping
    public List<String> getRoutes() {
        return camelContext.getRoutes().stream().map(Route::getId).collect(Collectors.toList());
    }
}
