package com.ngockhanh.clinic.catalog.application.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManagedServiceResponse(
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
    Instant updatedAt) {
  public static ManagedServiceResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.ManagedServiceReadQuery.Detail value) {
    return new ManagedServiceResponse(
        value.id(),
        value.code(),
        value.name(),
        value.serviceType(),
        value.performingDepartmentId(),
        value.performingDepartmentName(),
        value.specialtyId(),
        value.specialtyName(),
        value.unitPrice(),
        value.preparationInstruction(),
        value.active(),
        value.rowVersion(),
        value.createdAt(),
        value.updatedAt());
  }
}
