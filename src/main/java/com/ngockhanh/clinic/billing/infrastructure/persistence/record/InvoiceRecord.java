package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceRecord(
        UUID id,
        String invoiceNumber,
        UUID encounterId,
        UUID patientId,
        String invoiceType,
        String status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        Instant issuedAt,
        UUID createdByUserId
) {
}
