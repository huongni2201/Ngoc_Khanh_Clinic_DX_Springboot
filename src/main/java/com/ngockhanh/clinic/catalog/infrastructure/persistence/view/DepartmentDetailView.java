package com.ngockhanh.clinic.catalog.infrastructure.persistence.view;

import java.time.Instant;
import java.util.UUID;

/** SQL projection for catalog management; kept inside the persistence adapter. */
public record DepartmentDetailView(
    UUID id,
    String code,
    String name,
    String departmentType,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {}
