package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PatientConditionRecord(
        UUID id,
        UUID patientId,
        String diagnosisCode,
        String conditionName,
        String clinicalStatus,
        LocalDate onsetDate,
        LocalDate resolvedDate,
        String note,
        OffsetDateTime recordedAt
) {
}
