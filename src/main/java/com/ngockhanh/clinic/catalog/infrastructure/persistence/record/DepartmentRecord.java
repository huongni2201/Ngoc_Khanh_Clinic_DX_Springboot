package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record DepartmentRecord(
        UUID id,
        String departmentCode,
        String departmentName,
        String departmentType,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
