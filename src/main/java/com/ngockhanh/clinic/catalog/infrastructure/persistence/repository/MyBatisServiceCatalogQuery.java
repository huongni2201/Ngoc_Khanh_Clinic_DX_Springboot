package com.ngockhanh.clinic.catalog.infrastructure.persistence.repository;

import com.ngockhanh.clinic.catalog.application.ServiceCatalogQuery;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper.ServiceCatalogMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisServiceCatalogQuery implements ServiceCatalogQuery {
  private final ServiceCatalogMapper mapper;

  public List<Service> findByIds(Set<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper.findByIds(ids).stream()
        .map(
            r ->
                new Service(
                    r.id(),
                    r.serviceCode(),
                    r.serviceName(),
                    Boolean.TRUE.equals(r.isActive()),
                    Boolean.TRUE.equals(r.healthExaminationEligible())))
        .toList();
  }
}
