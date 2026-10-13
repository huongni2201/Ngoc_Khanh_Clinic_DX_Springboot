package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.template_versions}. */
@Builder
public record TemplateVersionRecord(
    UUID id,
    UUID templateId,
    int versionNo,
    UUID fileId,
    String paperSize,
    String orientation,
    String renderMode,
    Instant activeFrom,
    Instant retiredAt,
    Instant createdAt,
    String status,
    int contractVersion,
    BigDecimal pageWidthMm,
    BigDecimal pageHeightMm,
    Short pagesPerRecord,
    String validationReport,
    UUID previewFileId,
    UUID createdBy,
    UUID publishedBy,
    Instant publishedAt,
    String note,
    long rowVersion) {}
