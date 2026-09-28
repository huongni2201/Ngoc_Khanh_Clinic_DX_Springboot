package com.ngockhanh.clinic.healthcheck.api.response;

import java.time.Instant;
import java.util.UUID;

public record EmployeeListResponse(
        UUID batchEmployeeId,
        UUID employeeId,
        String employeeCode,
        String departmentName,
        String jobTitle,
        String occupation,
        AdministrativeSnapshotResponse snapshot,
        String status,
        Instant createdAt) {
}
