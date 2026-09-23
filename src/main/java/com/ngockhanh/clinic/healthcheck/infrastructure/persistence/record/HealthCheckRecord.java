package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record HealthCheckRecord(
        UUID id,
        String shsCode,
        String sourceType,
        UUID patientId,
        UUID healthCheckBatchEmployeeId,
        UUID encounterId,
        UUID masterTemplateVersionId,
        String fullNameSnapshot,
        LocalDate dateOfBirthSnapshot,
        String sexSnapshot,
        String cccdSnapshot,
        LocalDate cccdIssueDateSnapshot,
        String cccdIssuePlaceSnapshot,
        String ethnicitySnapshot,
        String subjectTypeSnapshot,
        String payerSourceSnapshot,
        String bloodGroupSnapshot,
        String phoneSnapshot,
        String provinceSnapshot,
        String wardSnapshot,
        String addressDetailSnapshot,
        String occupationSnapshot,
        String workplaceOrSchoolSnapshot,
        String healthCheckReasonSnapshot,
        LocalDate plannedExaminationDate,
        LocalDate actualExaminationDate,
        String status,
        UUID replacesHealthCheckRecordId,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        byte[] rowVersion
) {
}

