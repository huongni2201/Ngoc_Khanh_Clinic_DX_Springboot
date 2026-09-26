package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record PatientAllergyRecord(
        UUID id,
        UUID patientId,
        String substanceCode,
        String substanceName,
        String reaction,
        String severity,
        String verificationStatus,
        UUID recordedByUserId,
        OffsetDateTime recordedAt,
        OffsetDateTime endedAt
) {
}
