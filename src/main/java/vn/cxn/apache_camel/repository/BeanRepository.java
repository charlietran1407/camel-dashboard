package vn.cxn.apache_camel.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.cxn.apache_camel.model.entity.BeanEntity;

public interface BeanRepository extends JpaRepository<BeanEntity, UUID> {
    Optional<BeanEntity> findByBeanNameIgnoreCase(String beanName);
}
