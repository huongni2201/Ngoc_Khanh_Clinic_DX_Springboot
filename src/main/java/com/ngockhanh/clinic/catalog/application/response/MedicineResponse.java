package com.ngockhanh.clinic.catalog.application.response;

import com.ngockhanh.clinic.catalog.domain.aggregate.Medicine;
import java.time.Instant;
import java.util.UUID;

public record MedicineResponse(
    UUID id,
    String code,
    String genericName,
    String brandName,
    String strength,
    String dosageForm,
    String unit,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {

  public static MedicineResponse from(Medicine m) {
    return new MedicineResponse(
        m.id(),
        m.code().value(),
        m.genericName(),
        m.brandName(),
        m.strength(),
        m.dosageForm(),
        m.unit(),
        m.active(),
        m.rowVersion(),
        m.createdAt(),
        m.updatedAt());
  }

  public static MedicineResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.MedicineReadQuery.Detail value) {
    return new MedicineResponse(
        value.id(),
        value.code(),
        value.genericName(),
        value.brandName(),
        value.strength(),
        value.dosageForm(),
        value.unit(),
        value.active(),
        value.rowVersion(),
        value.createdAt(),
        value.updatedAt());
  }
}
