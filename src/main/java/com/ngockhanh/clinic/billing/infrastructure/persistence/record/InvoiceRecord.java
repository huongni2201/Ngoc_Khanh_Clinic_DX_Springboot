package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.invoices}. */
@Builder
public record InvoiceRecord(
    UUID id,
    UUID encounterId,
    String invoiceType,
    String status,
    Instant issuedAt,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
