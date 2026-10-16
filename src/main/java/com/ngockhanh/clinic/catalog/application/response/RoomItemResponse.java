package com.ngockhanh.clinic.catalog.application.response;

import java.time.Instant;
import java.util.UUID;

public record RoomItemResponse(
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
  public static RoomItemResponse fromRead(
      com.ngockhanh.clinic.catalog.application.port.out.RoomReadQuery.Item value) {
    return new RoomItemResponse(
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
