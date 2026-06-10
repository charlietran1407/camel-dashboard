package vn.cxn.apache_camel.service.mapper;

import vn.cxn.apache_camel.model.dto.DbConnectionDTO;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;

public interface DbConnectionMapper {

    DbConnectionDTO toDto(DbConnectionEntity entity, boolean maskPassword);

    DbConnectionEntity toEntity(DbConnectionDTO dto);
}
