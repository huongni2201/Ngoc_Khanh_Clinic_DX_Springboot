package com.ngockhanh.clinic.catalog.infrastructure.persistence.converter;

import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.SpecialtyRecord;

public final class SpecialtyPersistenceConverter {
  private SpecialtyPersistenceConverter() {}

  public static SpecialtyRecord toRecord(Specialty s) {
    return SpecialtyRecord.builder()
        .id(s.id())
        .code(s.code().value())
        .name(s.name())
        .active(s.active())
        .rowVersion(s.rowVersion())
        .createdAt(s.createdAt())
        .updatedAt(s.updatedAt())
        .build();
  }

  public static Specialty toDomain(SpecialtyRecord r) {
    if (r == null) return null;
    return Specialty.restore(
        r.id(),
        CatalogCode.restore(r.code()),
        r.name(),
        r.active(),
        r.createdAt(),
        r.updatedAt(),
        r.rowVersion());
  }
}
