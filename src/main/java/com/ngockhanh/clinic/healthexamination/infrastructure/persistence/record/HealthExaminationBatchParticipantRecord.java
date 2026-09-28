package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HealthExaminationBatchParticipantRecord(
        UUID id,
        UUID healthExaminationBatchId,
        UUID healthExaminationParticipantId,
        String participantCodeSnapshot,
        String departmentSnapshot,
        String jobTitleSnapshot,
        String occupationSnapshot,
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
        String administrativeOccupationSnapshot,
        String workplaceOrSchoolSnapshot,
        String healthExaminationReasonSnapshot,
        String status,
        Instant createdAt
) {
}
