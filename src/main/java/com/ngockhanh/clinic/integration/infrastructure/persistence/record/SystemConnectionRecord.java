package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.system_connections}. */
@Builder
public record SystemConnectionRecord(
    UUID id,
    String code,
    String name,
    String integrationType,
    String status,
    String configuration,
    String secretReference,
    Instant createdAt,
    Instant updatedAt) {}
