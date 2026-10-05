package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.util.UUID;

/** Persistence row of {@code public.invoice_lines}. */
public record InvoiceLineRecord(
    UUID id,
    UUID invoiceId,
    UUID serviceRequestId,
    UUID serviceId,
    String descriptionSnapshot,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal lineAmount,
    String status) {}
