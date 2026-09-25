package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record DocumentTemplateRecord(
        UUID id,
        String templateCode,
        String templateName,
        String templateType,
        String barcodePolicy,
        Boolean isMasterHealthExaminationForm,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
