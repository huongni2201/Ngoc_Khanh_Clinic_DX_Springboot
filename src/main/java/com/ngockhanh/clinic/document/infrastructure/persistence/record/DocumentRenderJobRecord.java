package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.document_render_jobs}. */
@Builder
public record DocumentRenderJobRecord(
    UUID id,
    String documentType,
    UUID requestedBy,
    UUID idempotencyKey,
    String context,
    UUID[] sourceIds,
    String status,
    int totalCount,
    int processedCount,
    String skipped,
    String errorCode,
    UUID templateVersionId,
    int attemptCount,
    UUID lockToken,
    Instant lockedUntil,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    Instant expiresAt,
    long rowVersion) {}
