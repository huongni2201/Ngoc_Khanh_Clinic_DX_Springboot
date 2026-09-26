package com.ngockhanh.clinic.notification.infrastructure.persistence.record;

import java.time.OffsetDateTime;
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
        OffsetDateTime scheduledAt,
        OffsetDateTime sentAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime failedAt,
        OffsetDateTime nextRetryAt,
        String errorCode,
        String errorMessage,
        OffsetDateTime createdAt
) {
}
