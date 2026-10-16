package com.ngockhanh.clinic.catalog.application.response;

import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import java.time.Instant;
import java.util.UUID;

public record SpecialtyResponse(
    UUID id,
    String code,
    String name,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {

  public static SpecialtyResponse from(Specialty s) {
    return new SpecialtyResponse(
        s.id(),
        s.code().value(),
        s.name(),
        s.active(),
        s.rowVersion(),
        s.createdAt(),
        s.updatedAt());
  }

  public static SpecialtyResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.SpecialtyReadQuery.Detail value) {
    return new SpecialtyResponse(
        value.id(),
        value.code(),
        value.name(),
        value.active(),
        value.rowVersion(),
        value.createdAt(),
        value.updatedAt());
  }
}
