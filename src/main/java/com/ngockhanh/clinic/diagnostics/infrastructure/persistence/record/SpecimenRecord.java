package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record SpecimenRecord(
        UUID id,
        String specimenCode,
        UUID patientId,
        UUID encounterId,
        String specimenType,
        String status,
        LocalDateTime collectedAt,
        UUID collectedByStaffId,
        LocalDateTime receivedAt
) {
}
