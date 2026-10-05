package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.rooms}. */
public record RoomRecord(
    UUID id, UUID departmentId, String code, String name, String roomType, boolean active) {}
