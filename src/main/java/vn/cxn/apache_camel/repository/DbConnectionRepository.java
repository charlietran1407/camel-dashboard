package vn.cxn.apache_camel.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;

public interface DbConnectionRepository extends JpaRepository<DbConnectionEntity, UUID> {
    Optional<DbConnectionEntity> findByDbId(String dbId);

    boolean existsByDbId(String dbId);
}
