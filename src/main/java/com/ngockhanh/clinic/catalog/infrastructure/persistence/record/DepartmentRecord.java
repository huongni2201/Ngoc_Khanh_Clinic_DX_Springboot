package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.departments}. */
public record DepartmentRecord(
    UUID id,
    String code,
    String name,
    String departmentType,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
