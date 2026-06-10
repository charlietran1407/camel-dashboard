package vn.cxn.apache_camel.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.cxn.apache_camel.model.entity.SystemLogEntity;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLogEntity, String> {

    /** All logs of a given type, sorted by timestamp DESC */
    List<SystemLogEntity> findByTypeIgnoreCaseOrderByTimestampDesc(String type);

    /** All logs sorted by timestamp DESC (no type filter) */
    List<SystemLogEntity> findAllByOrderByTimestampDesc();

    /** Delete all logs of a given type */
    @Modifying
    @Query("DELETE FROM SystemLogEntity e WHERE LOWER(e.type) = LOWER(:type)")
    void deleteByTypeIgnoreCase(String type);

    /** Keep only the latest N logs across all types (trim old logs) */
    @Query("SELECT e FROM SystemLogEntity e ORDER BY e.timestamp DESC")
    List<SystemLogEntity> findTopNByOrderByTimestampDesc(Pageable pageable);

    /** Delete logs whose IDs are not in the given collection of IDs */
    @Modifying
    @Query("DELETE FROM SystemLogEntity e WHERE e.id NOT IN :ids")
    void deleteByIdNotIn(
            @org.springframework.data.repository.query.Param("ids")
                    java.util.Collection<String> ids);
}
