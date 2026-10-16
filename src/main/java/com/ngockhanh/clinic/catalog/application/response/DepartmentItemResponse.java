package com.ngockhanh.clinic.catalog.application.response;

import java.time.Instant;
import java.util.UUID;

public record DepartmentItemResponse(
    UUID id,
    String code,
    String name,
    String departmentType,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {
  public static DepartmentItemResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.DepartmentReadQuery.Item value) {
    return new DepartmentItemResponse(
        value.id(),
        value.code(),
        value.name(),
        value.departmentType(),
        value.active(),
        value.rowVersion(),
        value.createdAt(),
        value.updatedAt());
  }
}
