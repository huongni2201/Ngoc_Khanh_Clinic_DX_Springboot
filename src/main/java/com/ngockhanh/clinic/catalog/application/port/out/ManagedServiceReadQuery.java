package com.ngockhanh.clinic.catalog.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagedServiceReadQuery {
  long count(
      String pattern,
      Boolean active,
      String serviceType,
      UUID performingDepartmentId,
      UUID specialtyId);

  List<Item> findPage(
      String pattern,
      Boolean active,
      String serviceType,
      UUID performingDepartmentId,
      UUID specialtyId,
      long offset,
      int limit,
      String sortKey,
      String sortBy);

  Optional<Detail> findDetailById(UUID id);

  record Item(
      UUID id,
      String code,
      String name,
      String serviceType,
      UUID performingDepartmentId,
      String performingDepartmentName,
      UUID specialtyId,
      String specialtyName,
      BigDecimal unitPrice,
      boolean active,
      long rowVersion,
      Instant createdAt,
      Instant updatedAt) {}

  record Detail(
      UUID id,
      String code,
      String name,
      String serviceType,
      UUID performingDepartmentId,
      String performingDepartmentName,
      UUID specialtyId,
      String specialtyName,
      BigDecimal unitPrice,
      String preparationInstruction,
      boolean active,
      long rowVersion,
      Instant createdAt,
      Instant updatedAt) {}
}
