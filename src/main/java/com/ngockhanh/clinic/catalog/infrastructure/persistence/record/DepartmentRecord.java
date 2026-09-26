package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record DepartmentRecord(
        UUID id,
        String departmentCode,
        String departmentName,
        String departmentType,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
