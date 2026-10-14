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
    String status,
    int contractVersion,
    String paperSize,
    String orientation,
    String renderMode,
    BigDecimal pageWidthMm,
    BigDecimal pageHeightMm,
    Short pagesPerRecord,
    Instant activeFrom,
    Instant retiredAt,
    String validationReport,
    UUID previewFileId,
    UUID createdBy,
    UUID publishedBy,
    Instant publishedAt,
    String note,
    Instant createdAt,
    long rowVersion) {}
