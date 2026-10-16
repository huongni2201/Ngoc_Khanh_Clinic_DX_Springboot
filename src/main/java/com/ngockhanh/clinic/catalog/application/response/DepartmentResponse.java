package com.ngockhanh.clinic.catalog.application.response;

import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(
    UUID id,
    String code,
    String name,
    String departmentType,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {

  public static DepartmentResponse from(Department d) {
    return new DepartmentResponse(
        d.id(),
        d.code().value(),
        d.name(),
        d.departmentType().name(),
        d.active(),
        d.rowVersion(),
        d.createdAt(),
        d.updatedAt());
  }

  public static DepartmentResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.DepartmentReadQuery.Detail value) {
    return new DepartmentResponse(
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
