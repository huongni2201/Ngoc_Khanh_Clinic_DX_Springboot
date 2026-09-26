package com.ngockhanh.clinic.notification.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record NotificationRecord(
        UUID id,
        UUID patientId,
        UUID appointmentId,
        UUID encounterId,
        String notificationType,
        String recipientPhone,
        String smsTemplateCode,
        String payloadJson,
        String providerCode,
        String providerMessageId,
        String idempotencyKey,
        String status,
        Integer attemptCount,
        Instant scheduledAt,
        Instant sentAt,
        Instant deliveredAt,
        Instant failedAt,
        Instant nextRetryAt,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
