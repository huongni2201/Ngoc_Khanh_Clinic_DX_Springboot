package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.system_connections}. */
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
