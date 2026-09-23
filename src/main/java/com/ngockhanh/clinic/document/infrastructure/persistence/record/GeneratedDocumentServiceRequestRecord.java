package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record GeneratedDocumentServiceRequestRecord(
        UUID id,
        UUID generatedDocumentId,
        UUID serviceRequestId,
        String serviceCodeSnapshot,
        String serviceNameSnapshot,
        Integer displayOrder,
        LocalDateTime createdAt
) {
}
