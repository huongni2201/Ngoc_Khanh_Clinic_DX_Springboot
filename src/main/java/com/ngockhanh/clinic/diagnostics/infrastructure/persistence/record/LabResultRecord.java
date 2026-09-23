package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record LabResultRecord(
        UUID id,
        UUID serviceRequestId,
        UUID specimenId,
        Integer versionNumber,
        String status,
        UUID supersedesLabResultId,
        String resultSource,
        UUID verifiedByStaffId,
        LocalDateTime verifiedAt,
        LocalDateTime finalizedAt,
        LocalDateTime createdAt,
        String rawMessageReference
) {
}
