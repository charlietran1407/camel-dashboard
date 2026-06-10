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
import org.springframework.test.util.ReflectionTestUtils;
import vn.cxn.apache_camel.exception.ConnectionTestException;
import vn.cxn.apache_camel.model.dto.DbConnectionDTO;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;
import vn.cxn.apache_camel.repository.DbConnectionRepository;
import vn.cxn.apache_camel.service.mapper.DbConnectionMapper;
import vn.cxn.apache_camel.service.mapper.DbConnectionMapperImpl;

class DbConnectionServiceTest {

    @Mock private DbConnectionRepository repository;

    @Mock private DynamicConnectionManager connectionManager;

    @Spy private JdbcUrlBuilder jdbcUrlBuilder = new JdbcUrlBuilder();

    @Spy private DbConnectionMapper mapper = new DbConnectionMapperImpl();

    @InjectMocks private DbConnectionService connectionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(connectionService, "encryptKey", "MySuperSecretKey123");
        ReflectionTestUtils.setField(connectionService, "encryptSalt", "cxn-apache-camel-salt-v1");
        connectionService.init();
    }

    @Test
    void testSaveConnectionRegistersInCamel() {
        DbConnectionDTO dto = new DbConnectionDTO();
        dto.setDbId("demoDb");
        dto.setType("postgresql");
        dto.setHost("localhost");
        dto.setPort(5432);
        dto.setDatabaseName("demo");
        dto.setUsername("postgres");
        dto.setPassword("secret");

        when(repository.saveAndFlush(any(DbConnectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DbConnectionDTO saved = connectionService.saveConnection(dto);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();

        verify(connectionManager, times(1))
                .registerDatabase(
                        eq("demoDb"),
                        eq("jdbc:postgresql://localhost:5432/demo"),
                        eq("postgres"),
                        eq("secret"),
                        isNull());
        verify(repository, times(1)).saveAndFlush(any(DbConnectionEntity.class));
    }

    @Test
    void testSaveConnectionHandlesUniqueConstraintViolation() {
        DbConnectionDTO dto = new DbConnectionDTO();
        dto.setDbId("demoDb");
        dto.setType("postgresql");
        dto.setHost("localhost");
        dto.setDatabaseName("demo");

        when(repository.saveAndFlush(any(DbConnectionEntity.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> connectionService.saveConnection(dto))
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
        DbConnectionDTO dto = new DbConnectionDTO();
        dto.setType("postgresql");
        dto.setHost("invalid-host-xxxx");
        dto.setDatabaseName("db");
        dto.setUsername("user");
        dto.setPassword("password=secret"); // Include password to check masking

        assertThatThrownBy(() -> connectionService.testConnection(dto))
                .isInstanceOf(ConnectionTestException.class)
                .hasMessageContaining("Connection test failed");
    }
}
