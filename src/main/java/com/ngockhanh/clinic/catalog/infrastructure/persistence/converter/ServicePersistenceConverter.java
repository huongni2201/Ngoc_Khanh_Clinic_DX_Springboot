package com.ngockhanh.clinic.catalog.infrastructure.persistence.converter;

import com.ngockhanh.clinic.catalog.domain.aggregate.CatalogService;
import com.ngockhanh.clinic.catalog.domain.enums.ServiceType;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.ServiceRecord;

public final class ServicePersistenceConverter {
  private ServicePersistenceConverter() {}

  public static ServiceRecord toRecord(CatalogService s) {
    return ServiceRecord.builder()
        .id(s.id())
        .code(s.code().value())
        .name(s.name())
        .serviceType(s.serviceType().name())
        .performingDepartmentId(s.performingDepartmentId())
        .specialtyId(s.specialtyId())
        .unitPrice(s.unitPrice())
        .preparationInstruction(s.preparationInstruction())
        .active(s.active())
        .rowVersion(s.rowVersion())
        .createdAt(s.createdAt())
        .updatedAt(s.updatedAt())
        .build();
  }

  public static CatalogService toDomain(ServiceRecord r) {
    if (r == null) return null;
    return CatalogService.restore(
        r.id(),
        CatalogCode.restore(r.code()),
        r.name(),
        ServiceType.valueOf(r.serviceType()),
        r.performingDepartmentId(),
        r.specialtyId(),
        r.unitPrice(),
        r.preparationInstruction(),
        r.active(),
        r.createdAt(),
        r.updatedAt(),
        r.rowVersion());
  }
}
