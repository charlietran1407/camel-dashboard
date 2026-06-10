package vn.cxn.apache_camel.service.mapper;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.cxn.apache_camel.model.dto.DbConnectionDTO;
import vn.cxn.apache_camel.model.entity.DbConnectionEntity;

@Component
public class DbConnectionMapperImpl implements DbConnectionMapper {

    @Override
    public DbConnectionDTO toDto(DbConnectionEntity entity, boolean maskPassword) {
        if (entity == null) {
            return null;
        }
        DbConnectionDTO dto = new DbConnectionDTO();
        dto.setId(entity.getId() != null ? entity.getId().toString() : null);
        dto.setDbId(entity.getDbId());
        dto.setType(entity.getType());
        dto.setHost(entity.getHost());
        dto.setPort(entity.getPort());
        dto.setDatabaseName(entity.getDatabaseName());
        dto.setUsername(entity.getUsername());

        if (maskPassword) {
            dto.setPassword("••••••••");
        } else {
            dto.setPassword(entity.getPassword());
        }

        dto.setQueryOptions(entity.getQueryOptions());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    @Override
    public DbConnectionEntity toEntity(DbConnectionDTO dto) {
        if (dto == null) {
            return null;
        }
        DbConnectionEntity entity = new DbConnectionEntity();
        if (dto.getId() != null && !dto.getId().isBlank()) {
            entity.setId(UUID.fromString(dto.getId()));
        }
        entity.setDbId(dto.getDbId());
        entity.setType(dto.getType());
        entity.setHost(dto.getHost());
        entity.setPort(dto.getPort());
        entity.setDatabaseName(dto.getDatabaseName());
        entity.setUsername(dto.getUsername());
        entity.setPassword(dto.getPassword());
        entity.setQueryOptions(dto.getQueryOptions());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        return entity;
    }
}
