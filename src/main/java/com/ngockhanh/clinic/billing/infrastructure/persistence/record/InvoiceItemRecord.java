package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;

public record InvoiceItemRecord(
        UUID id,
        UUID invoiceId,
        UUID serviceRequestId,
        UUID serviceId,
        String descriptionSnapshot,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal lineTotal
) {
}
