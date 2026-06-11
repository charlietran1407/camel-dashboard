package vn.cxn.apache_camel.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.cxn.apache_camel.model.entity.RouteVersionEntity;

public interface RouteVersionRepository extends JpaRepository<RouteVersionEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"service"})
    Optional<RouteVersionEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"service"})
    List<RouteVersionEntity> findByServiceId(UUID serviceId);

    void deleteByServiceId(UUID serviceId);

    @EntityGraph(attributePaths = {"service"})
    @Query("SELECT r FROM RouteVersionEntity r ORDER BY r.service.id ASC, r.version DESC")
    List<RouteVersionEntity> findAllByOrderByServiceIdAndVersionDesc();

    @EntityGraph(attributePaths = {"service"})
    @Query(
            "SELECT r.id as id, r.service as service, r.fileName as fileName, r.version as version,"
                + " r.autoRestore as autoRestore, r.uploadedAt as uploadedAt, r.updatedAt as"
                + " updatedAt, r.updatedBy as updatedBy, r.deployedAt as deployedAt, r.routeIds as"
                + " routeIds, r.originalRouteIds as originalRouteIds, r.routeDescriptions as"
                + " routeDescriptions, r.description as description, r.validateResult as"
                + " validateResult FROM RouteVersionEntity r ORDER BY r.service.id ASC, r.version"
                + " DESC")
    List<RouteVersionSummary> findAllSummary();

    @EntityGraph(attributePaths = {"service"})
    @Query(
            "SELECT r.id as id, r.service as service, r.fileName as fileName, r.version as version,"
                + " r.autoRestore as autoRestore, r.uploadedAt as uploadedAt, r.updatedAt as"
                + " updatedAt, r.updatedBy as updatedBy, r.deployedAt as deployedAt, r.routeIds as"
                + " routeIds, r.originalRouteIds as originalRouteIds, r.routeDescriptions as"
                + " routeDescriptions, r.description as description, r.validateResult as"
                + " validateResult FROM RouteVersionEntity r WHERE r.service.id = :serviceId")
    List<RouteVersionSummary> findSummaryByServiceId(@Param("serviceId") UUID serviceId);
}
