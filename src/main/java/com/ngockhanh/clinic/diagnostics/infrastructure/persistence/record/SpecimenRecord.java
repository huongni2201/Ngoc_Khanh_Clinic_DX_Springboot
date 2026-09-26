package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record SpecimenRecord(
        UUID id,
        String specimenCode,
        UUID patientId,
        UUID encounterId,
        String specimenType,
        String status,
        Instant collectedAt,
        UUID collectedByStaffId,
        Instant receivedAt
) {
}
