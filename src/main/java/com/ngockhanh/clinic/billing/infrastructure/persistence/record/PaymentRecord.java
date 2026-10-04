package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.payments}. */
public record PaymentRecord(
    UUID id,
    UUID invoiceId,
    String paymentMethod,
    BigDecimal amount,
    String status,
    String provider,
    String providerReference,
    Instant paidAt,
    UUID recordedBy,
    Instant updatedAt,
    long rowVersion,
    Instant createdAt) {}
