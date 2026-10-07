package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection.EndpointPermissionRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EndpointPermissionMyBatisMapper {
  List<EndpointPermissionRow> findAll();
}
