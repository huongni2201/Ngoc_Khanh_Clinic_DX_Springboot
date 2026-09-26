package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRecord(
        UUID id,
        UUID invoiceId,
        String paymentMethod,
        BigDecimal amount,
        String status,
        String gatewayTransactionId,
        UUID confirmedByUserId,
        Instant confirmedAt,
        Instant createdAt
) {
}
