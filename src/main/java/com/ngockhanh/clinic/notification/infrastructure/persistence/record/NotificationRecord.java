package com.ngockhanh.clinic.notification.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.notifications}. */
@Builder
public record NotificationRecord(
    UUID id,
    UUID batchId,
    String channel,
    String eventType,
    UUID patientId,
    UUID organizationId,
    String recipientSnapshot,
    String subject,
    String content,
    String deduplicationKey,
    String status,
    Instant scheduledAt,
    Instant sentAt,
    Instant createdAt,
    Instant updatedAt,
    UUID lockToken,
    Instant lockedUntil,
    long rowVersion) {}
