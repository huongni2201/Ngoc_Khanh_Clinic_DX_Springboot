package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record ImagingStudyRecord(
        UUID id,
        UUID serviceRequestId,
        String modality,
        String externalStudyUid,
        String deviceIdentifier,
        Instant studyAt,
        String metadataJson,
        Instant createdAt
) {
}
