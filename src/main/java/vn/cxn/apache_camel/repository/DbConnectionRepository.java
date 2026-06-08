package vn.cxn.apache_camel.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;

@Repository
public interface DbConnectionRepository extends JpaRepository<DbConnectionEntity, UUID> {
    Optional<DbConnectionEntity> findByDbId(String dbId);

    boolean existsByDbId(String dbId);
}
