package com.ngockhanh.clinic.catalog.application.response;

import java.time.Instant;
import java.util.UUID;

public record MedicineItemResponse(
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
  public static MedicineItemResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.MedicineReadQuery.Item value) {
    return new MedicineItemResponse(
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
