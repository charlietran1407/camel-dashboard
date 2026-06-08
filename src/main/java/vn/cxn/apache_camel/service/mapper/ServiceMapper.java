package vn.cxn.apache_camel.service.mapper;

import vn.cxn.apache_camel.model.dto.ServiceDTO;
import vn.cxn.apache_camel.model.entity.ServiceEntity;

public interface ServiceMapper {

    ServiceDTO toDto(ServiceEntity entity);
}
