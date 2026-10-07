package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.refunds}. */
@Builder
public record RefundRecord(
    UUID id,
    UUID paymentId,
    BigDecimal amount,
    String reason,
    String status,
    UUID requestedBy,
    UUID approvedBy,
    Instant approvedAt,
    String providerReference,
    Instant refundedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
