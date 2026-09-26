package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record PaymentAuthorizationRecord(
        UUID id,
        UUID serviceRequestId,
        String status,
        UUID invoiceItemId,
        Instant authorizedAt,
        UUID authorizedByUserId,
        String reason,
        Instant updatedAt
) {
}
