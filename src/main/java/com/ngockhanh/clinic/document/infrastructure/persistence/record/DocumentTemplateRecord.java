package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record DocumentTemplateRecord(
        UUID id,
        String templateCode,
        String templateName,
        String templateType,
        String barcodePolicy,
        Boolean isMasterHealthExaminationForm,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
