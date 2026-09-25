package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record HealthExaminationBatchEmployeeRecord(
        UUID id,
        UUID healthExaminationBatchId,
        UUID companyEmployeeId,
        String employeeCodeSnapshot,
        String departmentSnapshot,
        String jobTitleSnapshot,
        String occupationSnapshot,
        String administrativeSnapshotJson,
        String status,
        LocalDateTime createdAt
) {
}
