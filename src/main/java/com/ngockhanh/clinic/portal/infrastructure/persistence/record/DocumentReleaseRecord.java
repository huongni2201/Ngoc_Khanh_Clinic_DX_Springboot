package com.ngockhanh.clinic.portal.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.document_releases}. */
public record DocumentReleaseRecord(
    UUID id,
    UUID patientId,
    UUID issuedRepresentationId,
    UUID releasedBy,
    Instant releasedAt,
    UUID revokedBy,
    Instant revokedAt,
    String revocationReason) {}
