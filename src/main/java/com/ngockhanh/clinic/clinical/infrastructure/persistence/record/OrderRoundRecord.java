package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record OrderRoundRecord(
        UUID id,
        UUID encounterId,
        UUID healthExaminationRecordId,
        Integer roundNumber,
        String sourceType,
        String status,
        UUID orderedByStaffId,
        UUID createdByUserId,
        Instant orderedAt
) {
}
