package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record HealthExaminationRecord(
        UUID id,
        String shsCode,
        String sourceType,
        UUID patientId,
        UUID healthExaminationBatchEmployeeId,
        UUID encounterId,
        UUID masterTemplateVersionId,
        String fullNameSnapshot,
        LocalDate dateOfBirthSnapshot,
        String sexSnapshot,
        String identificationNumberSnapshot,
        LocalDate identificationNumberIssueDateSnapshot,
        String identificationNumberIssuePlaceSnapshot,
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
        String healthExaminationReasonSnapshot,
        LocalDate plannedExaminationDate,
        LocalDate actualExaminationDate,
        String status,
        UUID replacesHealthExaminationRecordId,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        byte[] rowVersion
) {
}

