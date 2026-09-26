package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record GeneratedDocumentRecord(
        UUID id,
        UUID encounterId,
        UUID documentTemplateVersionId,
        String documentKind,
        Integer printSequence,
        String paperSizeSnapshot,
        String orientationSnapshot,
        Integer versionNumber,
        String status,
        String renderPayloadHash,
        UUID fileAttachmentId,
        UUID generatedByUserId,
        Instant generatedAt,
        Instant invalidatedAt
) {
}
