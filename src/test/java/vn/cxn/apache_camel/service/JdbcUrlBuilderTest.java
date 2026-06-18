package vn.cxn.apache_camel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;

class JdbcUrlBuilderTest {

    private final JdbcUrlBuilder builder = new JdbcUrlBuilder();

    @Test
    void testPostgresUrlFormat() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("postgresql");
        conn.setHost("localhost");
        conn.setPort(5432);
        conn.setDatabaseName("mydb");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
    }

    @Test
    void testPostgresUrlDefaultPort() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("postgres");
        conn.setHost("localhost");
        conn.setPort(null); // Should fall back to 5432
        conn.setDatabaseName("mydb");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
    }

    @Test
    void testMysqlUrlFormat() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("mysql");
        conn.setHost("127.0.0.1");
        conn.setPort(3306);
        conn.setDatabaseName("schema");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("jdbc:mysql://127.0.0.1:3306/schema");
    }

    @Test
    void testMariadbUrlFormat() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("mariadb");
        conn.setHost("127.0.0.1");
        conn.setPort(3306);
        conn.setDatabaseName("schema");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("jdbc:mariadb://127.0.0.1:3306/schema");
    }

    @Test
    void testMssqlUrlFormat() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("mssql");
        conn.setHost("localhost");
        conn.setPort(1433);
        conn.setDatabaseName("db");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("jdbc:sqlserver://localhost:1433;databaseName=db");
    }

    @Test
    void testOracleUrlFormat() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("oracle");
        conn.setHost("localhost");
        conn.setPort(1521);
        conn.setDatabaseName("XE");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("jdbc:oracle:thin:@//localhost:1521/XE");
    }

    @Test
    void testMongoUrlWithoutCredentials() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("mongodb");
        conn.setHost("localhost");
        conn.setPort(27017);
        conn.setDatabaseName("admin");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("mongodb://localhost:27017/admin");
    }

    @Test
    void testMongoUrlWithCredentials() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("mongo");
        conn.setHost("localhost");
        conn.setPort(27017);
        conn.setDatabaseName("admin");
        conn.setUsername("root");
        conn.setPassword("secret");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("mongodb://root:secret@localhost:27017/admin");
    }

    @Test
    void testQueryOptionsMssql() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("mssql");
        conn.setHost("localhost");
        conn.setDatabaseName("db");
        conn.setQueryOptions("encrypt=true;trustServerCertificate=true");

        String url = builder.build(conn);
        assertThat(url)
                .isEqualTo(
                        "jdbc:sqlserver://localhost:1433;databaseName=db;encrypt=true;trustServerCertificate=true");
    }

    @Test
    void testQueryOptionsStandard() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("postgresql");
        conn.setHost("localhost");
        conn.setDatabaseName("mydb");
        conn.setQueryOptions("ssl=true&sslmode=require");

        String url = builder.build(conn);
        assertThat(url).isEqualTo("jdbc:postgresql://localhost:5432/mydb?ssl=true&sslmode=require");
    }

    @Test
    void testValidationThrowsForMissingFields() {
        assertThatThrownBy(() -> builder.build(null)).isInstanceOf(NullPointerException.class);

        DbConnectionEntity conn = new DbConnectionEntity();
        assertThatThrownBy(() -> builder.build(conn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");

        conn.setType("postgresql");
        assertThatThrownBy(() -> builder.build(conn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");

        conn.setHost("localhost");
        assertThatThrownBy(() -> builder.build(conn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void testValidationThrowsForUnsupportedType() {
        DbConnectionEntity conn = new DbConnectionEntity();
        conn.setType("invalidDb");
        conn.setHost("localhost");
        conn.setDatabaseName("db");

        assertThatThrownBy(() -> builder.build(conn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported database type");
    }
}
