package com.ngockhanh.clinic.catalog.infrastructure.persistence.view;

import java.time.Instant;
import java.util.UUID;

/** SQL projection for catalog management; kept inside the persistence adapter. */
public record RoomDetailView(
    UUID id,
    String code,
    String name,
    UUID departmentId,
    String departmentName,
    String roomType,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {}
