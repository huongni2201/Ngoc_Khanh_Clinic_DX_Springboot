package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDate;

public record StaffDepartmentAssignmentRecord(
        UUID id,
        UUID staffId,
        UUID departmentId,
        Boolean isPrimary,
        LocalDate validFrom,
        LocalDate validTo
) {
}
