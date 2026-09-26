package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SpecimenRecord(
        UUID id,
        String specimenCode,
        UUID patientId,
        UUID encounterId,
        String specimenType,
        String status,
        OffsetDateTime collectedAt,
        UUID collectedByStaffId,
        OffsetDateTime receivedAt
) {
}
