package com.ngockhanh.clinic.catalog.infrastructure.persistence.converter;

import com.ngockhanh.clinic.catalog.domain.aggregate.Room;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.RoomRecord;

public final class RoomPersistenceConverter {
  private RoomPersistenceConverter() {}

  public static RoomRecord toRecord(Room r) {
    return RoomRecord.builder()
        .id(r.id())
        .departmentId(r.departmentId())
        .code(r.code().value())
        .name(r.name())
        .roomType(r.roomType())
        .active(r.active())
        .rowVersion(r.rowVersion())
        .createdAt(r.createdAt())
        .updatedAt(r.updatedAt())
        .build();
  }

  public static Room toDomain(RoomRecord r) {
    if (r == null) return null;
    return Room.restore(
        r.id(),
        CatalogCode.restore(r.code()),
        r.name(),
        r.departmentId(),
        r.roomType(),
        r.active(),
        r.createdAt(),
        r.updatedAt(),
        r.rowVersion());
  }
}
