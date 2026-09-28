package com.ngockhanh.clinic.healthcheck.api.response;

import java.time.Instant;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;

public record EmployeeListResponse(
        UUID batchEmployeeId,
        UUID employeeId,
        String employeeCode,
        String departmentName,
        String jobTitle,
        String occupation,
        AdministrativeSnapshot snapshot,
        String status,
        Instant createdAt) {
}
