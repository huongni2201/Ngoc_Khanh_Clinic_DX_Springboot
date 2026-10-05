package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.result_series}. */
@Builder
public record ResultSeriesRecord(
    UUID id, UUID serviceRequestId, String resultType, Instant createdAt, long rowVersion) {}
