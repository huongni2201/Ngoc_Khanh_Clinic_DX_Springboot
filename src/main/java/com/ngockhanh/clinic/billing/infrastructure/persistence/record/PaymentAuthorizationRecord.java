package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record PaymentAuthorizationRecord(
        UUID id,
        UUID serviceRequestId,
        String status,
        UUID invoiceItemId,
        OffsetDateTime authorizedAt,
        UUID authorizedByUserId,
        String reason,
        OffsetDateTime updatedAt
) {
}
