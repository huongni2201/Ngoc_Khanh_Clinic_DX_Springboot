package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record InvoiceAdjustmentRecord(
        UUID id,
        UUID invoiceId,
        String adjustmentType,
        BigDecimal amount,
        String reason,
        UUID createdByUserId,
        OffsetDateTime createdAt
) {
}
