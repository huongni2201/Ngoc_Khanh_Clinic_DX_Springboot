package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record PatientAllergyRecord(
        UUID id,
        UUID patientId,
        String substanceCode,
        String substanceName,
        String reaction,
        String severity,
        String verificationStatus,
        UUID recordedByUserId,
        Instant recordedAt,
        Instant endedAt
) {
}
