package vn.cxn.apache_camel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.dao.DataIntegrityViolationException;
import vn.cxn.apache_camel.exception.ConnectionTestException;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;
import vn.cxn.apache_camel.repository.DbConnectionRepository;

class DbConnectionServiceTest {

    @Mock private DbConnectionRepository repository;

    @Mock private DynamicConnectionManager connectionManager;

    @Spy private JdbcUrlBuilder jdbcUrlBuilder = new JdbcUrlBuilder();

    @InjectMocks private DbConnectionService connectionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveConnectionRegistersInCamel() {
        DbConnectionEntity entity = new DbConnectionEntity();
        entity.setDbId("demoDb");
        entity.setType("postgresql");
        entity.setHost("localhost");
        entity.setPort(5432);
        entity.setDatabaseName("demo");
        entity.setUsername("postgres");
        entity.setPassword("secret");

        when(repository.saveAndFlush(any(DbConnectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DbConnectionEntity saved = connectionService.saveConnection(entity);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();

        verify(connectionManager, times(1))
                .registerDatabase(
                        eq("demoDb"),
                        eq("jdbc:postgresql://localhost:5432/demo"),
                        eq("postgres"),
                        eq("secret"),
                        isNull());
        verify(repository, times(1)).saveAndFlush(entity);
    }

    @Test
    void testSaveConnectionHandlesUniqueConstraintViolation() {
        DbConnectionEntity entity = new DbConnectionEntity();
        entity.setDbId("demoDb");
        entity.setType("postgresql");
        entity.setHost("localhost");
        entity.setDatabaseName("demo");

        when(repository.saveAndFlush(any(DbConnectionEntity.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> connectionService.saveConnection(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void testDeleteConnectionUnregistersFromCamel() {
        UUID id = UUID.randomUUID();
        DbConnectionEntity entity = new DbConnectionEntity();
        entity.setId(id);
        entity.setDbId("demoDb");

        when(repository.findById(id)).thenReturn(Optional.of(entity));

        connectionService.deleteConnection(id);

        verify(connectionManager, times(1)).unregisterDatabase(eq("demoDb"));
        verify(repository, times(1)).delete(entity);
    }

    @Test
    void testInitGlobalConnectionsStreamsAndRegisters() {
        DbConnectionEntity entity = new DbConnectionEntity();
        entity.setDbId("demoDb");
        entity.setType("postgresql");
        entity.setHost("localhost");
        entity.setDatabaseName("demo");

        when(repository.findAll()).thenReturn(List.of(entity));

        connectionService.initGlobalConnections();

        verify(connectionManager, times(1))
                .registerDatabase(
                        eq("demoDb"),
                        eq("jdbc:postgresql://localhost:5432/demo"),
                        isNull(),
                        isNull(),
                        isNull());
    }

    @Test
    void testMaskSensitiveInfo() {
        String jdbcUrl =
                "jdbc:postgresql://localhost:5432/db?user=postgres&password=my_secret_pass";
        String maskedJdbc = connectionService.maskSensitiveInfo(jdbcUrl);
        assertThat(maskedJdbc)
                .isEqualTo("jdbc:postgresql://localhost:5432/db?user=postgres&password=****");

        String mongoUrl = "mongodb://admin:secret123@localhost:27017/db";
        String maskedMongo = connectionService.maskSensitiveInfo(mongoUrl);
        assertThat(maskedMongo).isEqualTo("mongodb://admin:****@localhost:27017/db");

        String genericMsg = "Error connecting with password=abc123; host=localhost";
        String maskedMsg = connectionService.maskSensitiveInfo(genericMsg);
        assertThat(maskedMsg).isEqualTo("Error connecting with password=****; host=localhost");
    }

    @Test
    void testTestConnectionSqlFailureThrowsConnectionTestException() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("postgresql");
        conn.setHost("invalid-host-xxxx");
        conn.setDatabaseName("db");
        conn.setUsername("user");
        conn.setPassword("password=secret"); // Include password to check masking

        assertThatThrownBy(() -> connectionService.testConnection(conn))
                .isInstanceOf(ConnectionTestException.class)
                .hasMessageContaining("Connection test failed");
    }
}
