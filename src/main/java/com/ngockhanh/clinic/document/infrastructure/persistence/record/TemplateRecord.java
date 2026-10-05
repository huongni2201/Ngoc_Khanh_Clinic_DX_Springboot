package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.templates}. */
public record TemplateRecord(
    UUID id, String code, String name, String templateType, boolean active) {}
