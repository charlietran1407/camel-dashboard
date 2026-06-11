package vn.cxn.apache_camel.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.cxn.apache_camel.model.entity.RouteEntity;

public interface RouteRepository extends JpaRepository<RouteEntity, String> {

    @Override
    @EntityGraph(attributePaths = {"version", "version.service"})
    Optional<RouteEntity> findById(String id);

    List<RouteEntity> findByVersionId(UUID versionId);
}
