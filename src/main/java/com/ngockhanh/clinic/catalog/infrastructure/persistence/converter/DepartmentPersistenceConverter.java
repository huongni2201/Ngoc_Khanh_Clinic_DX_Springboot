package com.ngockhanh.clinic.catalog.infrastructure.persistence.converter;

import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.enums.DepartmentType;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.DepartmentRecord;

public final class DepartmentPersistenceConverter {
  private DepartmentPersistenceConverter() {}

  public static DepartmentRecord toRecord(Department d) {
    return DepartmentRecord.builder()
        .id(d.id())
        .code(d.code().value())
        .name(d.name())
        .departmentType(d.departmentType().name())
        .active(d.active())
        .createdAt(d.createdAt())
        .updatedAt(d.updatedAt())
        .rowVersion(d.rowVersion())
        .build();
  }

  public static Department toDomain(DepartmentRecord r) {
    if (r == null) return null;
    return Department.restore(
        r.id(),
        CatalogCode.restore(r.code()),
        r.name(),
        DepartmentType.valueOf(r.departmentType()),
        r.active(),
        r.createdAt(),
        r.updatedAt(),
        r.rowVersion());
  }
}
