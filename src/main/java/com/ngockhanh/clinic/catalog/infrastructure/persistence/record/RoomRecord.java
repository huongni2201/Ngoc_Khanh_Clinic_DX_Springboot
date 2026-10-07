package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.rooms}. */
@Builder
public record RoomRecord(
    UUID id, UUID departmentId, String code, String name, String roomType, boolean active) {}
