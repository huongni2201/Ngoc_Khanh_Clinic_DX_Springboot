package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record GeneratedDocumentServiceRequestRecord(
        UUID id,
        UUID generatedDocumentId,
        UUID serviceRequestId,
        String serviceCodeSnapshot,
        String serviceNameSnapshot,
        Integer displayOrder,
        OffsetDateTime createdAt
) {
}
