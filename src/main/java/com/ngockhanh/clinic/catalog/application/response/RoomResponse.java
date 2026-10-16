package com.ngockhanh.clinic.catalog.application.response;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
    UUID id,
    String code,
    String name,
    UUID departmentId,
    String departmentName,
    String roomType,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {
  public static RoomResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.RoomReadQuery.Detail value) {
    return new RoomResponse(
        value.id(),
        value.code(),
        value.name(),
        value.departmentId(),
        value.departmentName(),
        value.roomType(),
        value.active(),
        value.rowVersion(),
        value.createdAt(),
        value.updatedAt());
  }
}
