package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.idempotency_keys}. */
public record IdempotencyKeyRecord(
    UUID id,
    String scope,
    String actorKey,
    String requestKey,
    byte[] requestHash,
    String status,
    String resultResourceType,
    UUID resultResourceId,
    String resultSummary,
    Instant createdAt,
    Instant completedAt,
    Instant expiresAt) {}
