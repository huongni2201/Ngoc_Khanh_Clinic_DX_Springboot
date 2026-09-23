package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record PatientAllergyRecord(
        UUID id,
        UUID patientId,
        String substanceCode,
        String substanceName,
        String reaction,
        String severity,
        String verificationStatus,
        UUID recordedByUserId,
        LocalDateTime recordedAt,
        LocalDateTime endedAt
) {
}
