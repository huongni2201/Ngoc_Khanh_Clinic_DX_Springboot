package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.outbox_events}. */
public record OutboxEventRecord(
    UUID id,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    String payload,
    String status,
    Instant availableAt,
    int attemptCount,
    Instant processedAt,
    String lastError,
    Instant createdAt,
    UUID lockToken,
    Instant lockedUntil,
    long rowVersion) {}
