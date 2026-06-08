package vn.cxn.apache_camel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import vn.cxn.apache_camel.model.entity.RouteEntity;
import vn.cxn.apache_camel.repository.RouteRepository;

class RouteStateServiceTest {

    @Mock private RouteRepository routeRepository;

    @InjectMocks private RouteStateService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getDesiredState_ReturnsStateFromRepository() {
        RouteEntity route = new RouteEntity();
        route.setRouteId("orders-route");
        route.setDesiredState("Stopped");

        when(routeRepository.findById("orders-route")).thenReturn(Optional.of(route));

        Optional<String> state = service.getDesiredState("orders-route");
        assertThat(state).contains("Stopped");
    }

    @Test
    void saveDesiredState_UpdatesRouteEntity() {
        RouteEntity route = new RouteEntity();
        route.setRouteId("orders-route");
        route.setDesiredState("Started");

        when(routeRepository.findById("orders-route")).thenReturn(Optional.of(route));

        service.saveDesiredState("orders-route", "Stopped");

        assertThat(route.getDesiredState()).isEqualTo("Stopped");
        verify(routeRepository, times(1)).save(route);
    }

    @Test
    void removeDesiredState_DeletesRouteEntityIfExists() {
        when(routeRepository.existsById("orders-route")).thenReturn(true);

        service.removeDesiredState("orders-route");

        verify(routeRepository, times(1)).deleteById("orders-route");
    }

    @Test
    void deleteByVersionId_DeletesRoutesAssociatedWithVersion() {
        java.util.UUID versionId = java.util.UUID.randomUUID();
        java.util.List<RouteEntity> routes = java.util.List.of(new RouteEntity());
        when(routeRepository.findByVersionId(versionId)).thenReturn(routes);

        service.deleteByVersionId(versionId);

        verify(routeRepository, times(1)).deleteAll(routes);
    }
}
