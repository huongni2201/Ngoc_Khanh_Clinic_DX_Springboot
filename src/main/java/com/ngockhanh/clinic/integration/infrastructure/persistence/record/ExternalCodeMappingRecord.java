package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.external_code_mappings}. */
@Builder
public record ExternalCodeMappingRecord(
    UUID id,
    UUID connectionId,
    String mappingType,
    String internalCode,
    String externalCode,
    boolean active) {}
