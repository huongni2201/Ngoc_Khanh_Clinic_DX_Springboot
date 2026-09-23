package com.ngockhanh.clinic.notification.infrastructure.persistence.record;

import java.time.LocalDateTime;
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
        LocalDateTime scheduledAt,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime failedAt,
        LocalDateTime nextRetryAt,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt
) {
}
