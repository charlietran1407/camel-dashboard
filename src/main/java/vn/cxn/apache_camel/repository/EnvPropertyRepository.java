package vn.cxn.apache_camel.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.cxn.apache_camel.model.entity.EnvPropertyEntity;

public interface EnvPropertyRepository extends JpaRepository<EnvPropertyEntity, UUID> {
    Optional<EnvPropertyEntity> findByKey(String key);

    boolean existsByKey(String key);

    void deleteByKey(String key);
}
