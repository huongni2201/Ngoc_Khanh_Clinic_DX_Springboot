package com.ngockhanh.clinic.healthexamination.application.port;

import java.time.Instant;
import java.util.UUID;

public record ImportAttachmentMetadata(
        UUID id,
        UUID importJobId,
        UUID createdByUserId,
        String storageKey,
        String fileName,
        String mimeType,
        long sizeBytes,
        String sha256,
        Instant createdAt) {
}
