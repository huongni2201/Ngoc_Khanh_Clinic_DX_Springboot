package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.external_code_mappings}. */
public record ExternalCodeMappingRecord(
    UUID id,
    UUID connectionId,
    String mappingType,
    String internalCode,
    String externalCode,
    boolean active) {}
