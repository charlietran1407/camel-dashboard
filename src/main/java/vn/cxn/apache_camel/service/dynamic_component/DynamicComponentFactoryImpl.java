package vn.cxn.apache_camel.service.dynamic_component;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.apache.camel.component.sql.SqlComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DynamicComponentFactoryImpl implements DynamicComponentFactory {

    @Value("${app.datasource.hikari.min-idle:1}")
    private int minIdle;

    @Value("${app.datasource.hikari.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${app.datasource.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    @Value("${app.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${app.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Override
    public DataSource createDataSource(String url, String username, String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMinimumIdle(minIdle);
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setMaxLifetime(maxLifetime);
        return dataSource;
    }

    @Override
    public org.apache.camel.Component createSqlComponent(DataSource dataSource) {
        SqlComponent sqlComponent = new SqlComponent();
        sqlComponent.setDataSource(dataSource);
        return sqlComponent;
    }

    @Override
    public com.mongodb.client.MongoClient createMongoClient(String connectionUri) {
        return com.mongodb.client.MongoClients.create(connectionUri);
    }

    @Override
    public org.apache.camel.Component createMongoDbComponent(
            com.mongodb.client.MongoClient mongoClient) {
        org.apache.camel.component.mongodb.MongoDbComponent mongoComponent =
                new org.apache.camel.component.mongodb.MongoDbComponent();
        mongoComponent.setMongoConnection(mongoClient);
        return mongoComponent;
    }
}
