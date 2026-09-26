package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record DocumentTemplateRecord(
        UUID id,
        String templateCode,
        String templateName,
        String templateType,
        String barcodePolicy,
        Boolean isMasterHealthExaminationForm,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
