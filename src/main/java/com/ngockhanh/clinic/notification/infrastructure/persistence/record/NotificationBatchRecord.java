package com.ngockhanh.clinic.notification.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.notification_batches}. */
@Builder
public record NotificationBatchRecord(
    UUID id,
    String eventType,
    String channel,
    String status,
    UUID createdBy,
    Instant createdAt,
    Instant completedAt,
    long rowVersion) {}
