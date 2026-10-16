package com.ngockhanh.clinic.catalog.infrastructure.persistence.converter;

import com.ngockhanh.clinic.catalog.domain.aggregate.Medicine;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.MedicineRecord;

public final class MedicinePersistenceConverter {
  private MedicinePersistenceConverter() {}

  public static MedicineRecord toRecord(Medicine m) {
    return MedicineRecord.builder()
        .id(m.id())
        .code(m.code().value())
        .genericName(m.genericName())
        .brandName(m.brandName())
        .strength(m.strength())
        .dosageForm(m.dosageForm())
        .unit(m.unit())
        .active(m.active())
        .rowVersion(m.rowVersion())
        .createdAt(m.createdAt())
        .updatedAt(m.updatedAt())
        .build();
  }

  public static Medicine toDomain(MedicineRecord r) {
    if (r == null) return null;
    return Medicine.restore(
        r.id(),
        CatalogCode.restore(r.code()),
        r.genericName(),
        r.brandName(),
        r.strength(),
        r.dosageForm(),
        r.unit(),
        r.active(),
        r.createdAt(),
        r.updatedAt(),
        r.rowVersion());
  }
}
