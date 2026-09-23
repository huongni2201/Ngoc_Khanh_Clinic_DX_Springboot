package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record OrderRoundRecord(
        UUID id,
        UUID encounterId,
        UUID healthCheckRecordId,
        Integer roundNumber,
        String sourceType,
        String status,
        UUID orderedByStaffId,
        UUID createdByUserId,
        LocalDateTime orderedAt
) {
}
