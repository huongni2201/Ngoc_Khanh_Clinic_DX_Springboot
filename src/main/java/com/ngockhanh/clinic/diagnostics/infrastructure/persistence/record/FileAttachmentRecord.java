package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record FileAttachmentRecord(
        UUID id,
        UUID patientId,
        UUID encounterId,
        String entityType,
        UUID entityId,
        String documentType,
        String storageProvider,
        String storageKey,
        String fileName,
        String mimeType,
        Long sizeBytes,
        String sha256,
        UUID createdByUserId,
        Instant createdAt
) {
}
