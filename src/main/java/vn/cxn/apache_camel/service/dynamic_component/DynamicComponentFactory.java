package vn.cxn.apache_camel.service.dynamic_component;

import com.mongodb.client.MongoClient;
import javax.sql.DataSource;
import org.apache.camel.Component;

public interface DynamicComponentFactory {

    DataSource createDataSource(String url, String username, String password);

    Component createSqlComponent(DataSource dataSource);

    MongoClient createMongoClient(String connectionUri);

    Component createMongoDbComponent(MongoClient mongoClient);
}
