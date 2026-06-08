package vn.cxn.apache_camel.service;

import static org.mockito.Mockito.*;

import javax.sql.DataSource;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.component.sql.SqlComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DynamicConnectionManagerTest {

    @Mock private CamelContext camelContext;

    @Mock
    private vn.cxn.apache_camel.service.dynamic_component.DynamicComponentFactory
            dynamicComponentFactory;

    @InjectMocks private DynamicConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        org.apache.camel.spi.Registry registry = mock(org.apache.camel.spi.Registry.class);
        when(camelContext.getRegistry()).thenReturn(registry);

        // Mock DynamicComponentFactory behavior to return mock DataSource and
        // SqlComponent
        DataSource mockDataSource = mock(DataSource.class);
        when(dynamicComponentFactory.createDataSource(any(), any(), any()))
                .thenReturn(mockDataSource);
        when(dynamicComponentFactory.createSqlComponent(any()))
                .thenReturn(mock(SqlComponent.class));
    }

    @Test
    void testRegisterDatabaseAndReferenceCounting() throws Exception {
        String dbId = "testDb";
        String url = "jdbc:postgresql://localhost:5432/testdb";
        String user = "postgres";
        String pass = "secret";

        // Mock CamelContext behavior for component lookup
        when(camelContext.getComponent(eq(dbId), anyBoolean())).thenReturn(null);

        // 1. Register for RouteA
        connectionManager.registerDatabase(dbId, url, user, pass, "RouteA");

        // Verify component was added to CamelContext
        verify(camelContext, times(1)).addComponent(eq(dbId), any(SqlComponent.class));

        // Reset mock interactions for clean assertion on the next steps
        clearInvocations(camelContext);

        // 2. Register for RouteB (Same config, should skip new component creation but
        // track RouteB)
        // Simulate that component now exists in context
        when(camelContext.getComponent(eq(dbId), anyBoolean())).thenReturn(mock(Component.class));
        connectionManager.registerDatabase(dbId, url, user, pass, "RouteB");

        // Verify it was NOT re-added to CamelContext since it's already active
        verify(camelContext, never()).addComponent(anyString(), any(Component.class));

        // 3. Cleanup RouteA (RouteB is still active, pool must NOT be closed)
        connectionManager.cleanupConnectionsForRoute("RouteA");

        // Verify it was NOT removed from CamelContext
        verify(camelContext, never()).removeComponent(anyString());

        // 4. Cleanup RouteB (Zero references remain, pool MUST be closed)
        connectionManager.cleanupConnectionsForRoute("RouteB");

        // Verify the component was successfully stopped and removed from CamelContext
        verify(camelContext, times(1)).removeComponent(eq(dbId));
    }
}
