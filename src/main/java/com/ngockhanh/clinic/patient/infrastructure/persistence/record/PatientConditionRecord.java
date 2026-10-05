package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.patient_conditions}. */
@Builder
public record PatientConditionRecord(
    UUID id,
    UUID patientId,
    String diagnosisCode,
    String conditionName,
    String clinicalStatus,
    LocalDate onsetDate,
    LocalDate resolvedDate,
    String note,
    Instant recordedAt,
    UUID recordedBy) {}
