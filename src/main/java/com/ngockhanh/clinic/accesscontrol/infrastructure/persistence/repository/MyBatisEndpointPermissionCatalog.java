package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.application.port.EndpointPermissionCatalog;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.EndpointPermissionMyBatisMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisEndpointPermissionCatalog implements EndpointPermissionCatalog {
  private final EndpointPermissionMyBatisMapper mapper;

  @Override
  public List<EndpointPermission> findAll() {
    return mapper.findAll().stream()
        .map(row -> new EndpointPermission(row.httpMethod(), row.endpoint(), row.permissionCode()))
        .toList();
  }
}
