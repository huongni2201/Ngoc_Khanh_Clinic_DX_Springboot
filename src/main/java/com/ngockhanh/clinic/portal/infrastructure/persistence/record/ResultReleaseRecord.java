package com.ngockhanh.clinic.portal.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.result_releases}. */
public record ResultReleaseRecord(
    UUID id,
    UUID resultVersionId,
    UUID patientId,
    UUID releasedBy,
    Instant releasedAt,
    UUID revokedBy,
    Instant revokedAt,
    String revocationReason) {}
