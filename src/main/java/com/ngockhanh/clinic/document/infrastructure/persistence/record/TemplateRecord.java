package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.templates}. */
@Builder
public record TemplateRecord(
    UUID id, String code, String name, String templateType, boolean active) {}
