package com.ngockhanh.clinic.catalog.application.response;

import java.time.Instant;
import java.util.UUID;

public record SpecialtyItemResponse(
    UUID id,
    String code,
    String name,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {
  public static SpecialtyItemResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.SpecialtyReadQuery.Item value) {
    return new SpecialtyItemResponse(
        value.id(),
        value.code(),
        value.name(),
        value.active(),
        value.rowVersion(),
        value.createdAt(),
        value.updatedAt());
  }
}
