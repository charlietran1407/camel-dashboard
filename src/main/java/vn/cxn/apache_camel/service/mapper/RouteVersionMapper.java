package vn.cxn.apache_camel.service.mapper;

import vn.cxn.apache_camel.model.dto.RouteVersion;
import vn.cxn.apache_camel.model.entity.RouteVersionEntity;
import vn.cxn.apache_camel.repository.RouteVersionSummary;

public interface RouteVersionMapper {

    RouteVersion toModel(RouteVersionEntity entity);

    RouteVersion toModel(RouteVersionSummary summary);
}
