package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record ImagingStudyRecord(
        UUID id,
        UUID serviceRequestId,
        String modality,
        String externalStudyUid,
        String deviceIdentifier,
        LocalDateTime studyAt,
        String metadataJson,
        LocalDateTime createdAt
) {
}
