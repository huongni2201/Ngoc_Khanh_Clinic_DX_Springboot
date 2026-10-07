package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** A row in public.import_jobs. */
@Builder
public record ImportJobRecord(
    UUID id,
    String importType,
    UUID batchId,
    String configuration,
    UUID sourceFileId,
    String status,
    UUID createdBy,
    UUID confirmedBy,
    Instant createdAt,
    Instant confirmedAt,
    Instant cancelledAt,
    Instant expiresAt,
    String confirmedResult,
    long rowVersion) {}
