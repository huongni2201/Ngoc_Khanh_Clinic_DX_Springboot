package com.ngockhanh.clinic.notification.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.notification_attempts}. */
@Builder
public record NotificationAttemptRecord(
    UUID id,
    UUID notificationId,
    int attemptNo,
    String provider,
    String providerMessageId,
    String status,
    String errorCode,
    String errorMessage,
    Instant attemptedAt,
    UUID claimToken,
    Instant completedAt) {}
