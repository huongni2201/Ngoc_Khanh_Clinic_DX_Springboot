package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record PaymentAuthorizationRecord(
        UUID id,
        UUID serviceRequestId,
        String status,
        UUID invoiceItemId,
        LocalDateTime authorizedAt,
        UUID authorizedByUserId,
        String reason,
        LocalDateTime updatedAt
) {
}
