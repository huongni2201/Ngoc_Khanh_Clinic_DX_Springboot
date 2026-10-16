package com.ngockhanh.clinic.catalog.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicineReadQuery {
  long count(String pattern, Boolean active, String dosageForm);

  List<Item> findPage(
      String pattern,
      Boolean active,
      String dosageForm,
      long offset,
      int limit,
      String sortKey,
      String sortBy);

  Optional<Detail> findDetailById(UUID id);

  record Item(
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
      Instant updatedAt) {}

  record Detail(
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
      Instant updatedAt) {}
}
