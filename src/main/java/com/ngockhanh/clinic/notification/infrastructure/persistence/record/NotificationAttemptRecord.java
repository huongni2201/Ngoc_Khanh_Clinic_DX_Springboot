package com.ngockhanh.clinic.notification.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record NotificationAttemptRecord(
        UUID id,
        UUID notificationId,
        Integer attemptNumber,
        String providerCode,
        String providerMessageId,
        String status,
        String requestReference,
        String responseReference,
        String errorCode,
        String errorMessage,
        Instant attemptedAt
) {
}
