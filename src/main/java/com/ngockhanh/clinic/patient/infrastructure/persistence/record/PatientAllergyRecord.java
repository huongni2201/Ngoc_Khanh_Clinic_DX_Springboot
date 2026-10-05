package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.patient_allergies}. */
public record PatientAllergyRecord(
    UUID id,
    UUID patientId,
    String substance,
    String reaction,
    String severity,
    String status,
    Instant recordedAt,
    UUID recordedBy) {}
