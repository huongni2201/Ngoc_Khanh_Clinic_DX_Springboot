package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.service_template_mappings}. */
@Builder
public record ServiceTemplateMappingRecord(
    UUID id, UUID serviceId, UUID templateId, Instant activeFrom, Instant retiredAt) {}
