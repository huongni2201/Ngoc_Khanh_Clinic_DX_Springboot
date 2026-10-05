package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.template_versions}. */
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
    Instant createdAt) {}
