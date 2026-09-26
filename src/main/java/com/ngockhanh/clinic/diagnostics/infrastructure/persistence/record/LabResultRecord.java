package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.Instant;
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
        Instant verifiedAt,
        Instant finalizedAt,
        Instant createdAt,
        String rawMessageReference,
        Instant releasedToPatientAt,
        UUID releasedToPatientByUserId
) {
}
