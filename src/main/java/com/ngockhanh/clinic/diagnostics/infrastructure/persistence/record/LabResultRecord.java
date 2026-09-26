package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.OffsetDateTime;
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
        OffsetDateTime verifiedAt,
        OffsetDateTime finalizedAt,
        OffsetDateTime createdAt,
        String rawMessageReference,
        OffsetDateTime releasedToPatientAt,
        UUID releasedToPatientByUserId
) {
}
