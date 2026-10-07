package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.service_requests}. */
@Builder
public record ServiceRequestRecord(
    UUID id,
    UUID orderRoundId,
    UUID serviceId,
    String status,
    UUID requestedBy,
    Instant requestedAt,
    UUID assignedDepartmentId,
    UUID assignedRoomId,
    UUID assignedStaffId,
    BigDecimal referencePriceSnapshot,
    BigDecimal unitPriceSnapshot,
    String pricingSource,
    Instant startedAt,
    Instant completedAt,
    Instant cancelledAt,
    String cancelReason,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
