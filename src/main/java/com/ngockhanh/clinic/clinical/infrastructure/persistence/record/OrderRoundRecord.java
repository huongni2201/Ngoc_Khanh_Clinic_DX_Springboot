package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.order_rounds}. */
public record OrderRoundRecord(
    UUID id,
    UUID encounterId,
    int roundNo,
    String entrySource,
    UUID orderedBy,
    Instant orderedAt,
    String status,
    String note) {}
