package vn.cxn.apache_camel.service.mapper;

import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.dto.ServiceDTO;
import vn.cxn.apache_camel.model.entity.ServiceEntity;

@Component
public class ServiceMapperImpl implements ServiceMapper {

    @Override
    public ServiceDTO toDto(ServiceEntity entity) {
        if (entity == null) {
            return null;
        }
        ServiceDTO service = new ServiceDTO();
        service.setId(entity.getId().toString());
        service.setName(entity.getName());
        service.setDescription(entity.getDescription());
        service.setCreatedAt(entity.getCreatedAt());
        service.setUpdatedAt(entity.getUpdatedAt());
        return service;
    }
}
