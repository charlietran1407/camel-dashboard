package vn.cxn.apache_camel.service;

import static org.mockito.Mockito.*;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import vn.cxn.apache_camel.model.dto.RouteVersion;

class RouteAutoRestoreServiceTest {

    @Mock private CamelRouteService camelRouteService;
    @Mock private RouteVersionService versionService;
    @Mock private CamelContext camelContext;
    @Mock private Tracer tracer;

    private RouteAutoRestoreService autoRestoreService;
    private final Executor directExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        Span mockSpan = mock(Span.class);
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class);
        when(tracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.startSpan()).thenReturn(mockSpan);
        when(mockSpan.makeCurrent()).thenReturn(mock(Scope.class));

        // Initialize with 0ms delay and direct executor for fast and deterministic unit testing
        autoRestoreService =
                new RouteAutoRestoreService(
                        camelRouteService, versionService, camelContext, tracer, 0, directExecutor);
    }

    @Test
    void testRestoreActiveRoutes_EmptyList() throws Exception {
        when(versionService.getAllVersions()).thenReturn(Collections.emptyList());

        autoRestoreService.restoreActiveRoutes();

        verify(camelRouteService, never()).restoreRouteVersion(any(), any());
    }

    @Test
    void testRestoreActiveRoutes_NoAutoRestore() throws Exception {
        RouteVersion v1 = new RouteVersion();
        v1.setId("1");
        v1.setAutoRestore(false);
        v1.setServiceId("serviceA");

        when(versionService.getAllVersions()).thenReturn(Collections.singletonList(v1));

        autoRestoreService.restoreActiveRoutes();

        verify(camelRouteService, never()).restoreRouteVersion(any(), any());
    }

    @Test
    void testRestoreActiveRoutes_LatestVersionSelectedAndRestored() throws Exception {
        // v1: version 1, auto-restore = true
        RouteVersion v1 = new RouteVersion();
        v1.setId("1");
        v1.setAutoRestore(true);
        v1.setServiceId("serviceA");
        v1.setVersion(1);
        v1.setFileName("serviceA-v1.yaml");

        // v2: version 2, auto-restore = true (should be selected over v1)
        RouteVersion v2 = new RouteVersion();
        v2.setId("2");
        v2.setAutoRestore(true);
        v2.setServiceId("serviceA");
        v2.setVersion(2);
        v2.setFileName("serviceA-v2.yaml");

        // v3: another service, auto-restore = true
        RouteVersion v3 = new RouteVersion();
        v3.setId("3");
        v3.setAutoRestore(true);
        v3.setServiceId("serviceB");
        v3.setVersion(1);
        v3.setFileName("serviceB-v1.yaml");

        when(versionService.getAllVersions()).thenReturn(Arrays.asList(v1, v2, v3));
        when(camelContext.getRoute(anyString())).thenReturn(null);

        autoRestoreService.restoreActiveRoutes();

        // Verify latest version of serviceA (v2) and serviceB (v3) are restored
        verify(camelRouteService, times(1)).restoreRouteVersion(eq(v2), any());
        verify(camelRouteService, times(1)).restoreRouteVersion(eq(v3), any());
        verify(camelRouteService, never()).restoreRouteVersion(eq(v1), any());
    }

    @Test
    void testRestoreActiveRoutes_SkipAlreadyInContext() throws Exception {
        RouteVersion v1 = new RouteVersion();
        v1.setId("1");
        v1.setAutoRestore(true);
        v1.setServiceId("serviceA");
        v1.setVersion(1);
        v1.setRouteIds(Collections.singletonList("routeA"));

        when(versionService.getAllVersions()).thenReturn(Collections.singletonList(v1));
        // Simulate that route "routeA" already exists in context
        when(camelContext.getRoute("routeA")).thenReturn(mock(Route.class));

        autoRestoreService.restoreActiveRoutes();

        // Should skip restore since route is already in context
        verify(camelRouteService, never()).restoreRouteVersion(any(), any());
    }

    @Test
    void testRestoreActiveRoutes_HandlesExceptionGracefully() throws Exception {
        RouteVersion v1 = new RouteVersion();
        v1.setId("1");
        v1.setAutoRestore(true);
        v1.setServiceId("serviceA");
        v1.setVersion(1);

        RouteVersion v2 = new RouteVersion();
        v2.setId("2");
        v2.setAutoRestore(true);
        v2.setServiceId("serviceB");
        v2.setVersion(1);

        when(versionService.getAllVersions()).thenReturn(Arrays.asList(v1, v2));
        when(camelContext.getRoute(anyString())).thenReturn(null);

        // Make v1 fail to restore, but v2 should still proceed and succeed
        doThrow(new RuntimeException("Simulated restore failure"))
                .when(camelRouteService)
                .restoreRouteVersion(eq(v1), any());

        autoRestoreService.restoreActiveRoutes();

        // Verify both were attempted
        verify(camelRouteService, times(1)).restoreRouteVersion(eq(v1), any());
        verify(camelRouteService, times(1)).restoreRouteVersion(eq(v2), any());
    }
}
