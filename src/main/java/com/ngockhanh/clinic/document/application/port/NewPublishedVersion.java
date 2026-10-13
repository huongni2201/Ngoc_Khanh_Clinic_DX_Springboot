package com.ngockhanh.clinic.document.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A published system version to insert into {@code public.template_versions}. */
public record NewPublishedVersion(
    UUID id,
    UUID templateId,
    int versionNo,
    UUID fileId,
    int contractVersion,
    String renderMode,
    String paperSize,
    String orientation,
    BigDecimal pageWidthMm,
    BigDecimal pageHeightMm,
    Short pagesPerRecord,
    Instant publishedAt) {}
