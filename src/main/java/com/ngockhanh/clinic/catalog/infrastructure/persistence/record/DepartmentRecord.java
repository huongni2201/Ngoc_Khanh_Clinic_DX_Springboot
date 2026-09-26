package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record DepartmentRecord(
        UUID id,
        String departmentCode,
        String departmentName,
        String departmentType,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
