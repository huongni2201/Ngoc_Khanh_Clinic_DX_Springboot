package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImagingStudyRecord(
        UUID id,
        UUID serviceRequestId,
        String modality,
        String externalStudyUid,
        String deviceIdentifier,
        OffsetDateTime studyAt,
        String metadataJson,
        OffsetDateTime createdAt
) {
}
