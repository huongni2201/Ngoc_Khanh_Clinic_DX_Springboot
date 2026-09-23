package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DocumentTemplateVersionRecord(
        UUID id,
        UUID documentTemplateId,
        Integer versionNumber,
        UUID sourceFileAttachmentId,
        String paperSize,
        BigDecimal customWidthMm,
        BigDecimal customHeightMm,
        String orientation,
        String renderMode,
        String rendererType,
        String schemaJson,
        String renderTemplate,
        LocalDateTime effectiveFrom,
        LocalDateTime retiredAt,
        UUID createdByUserId,
        LocalDateTime createdAt
) {
}
