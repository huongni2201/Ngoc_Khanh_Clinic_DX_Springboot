package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.result_versions}. */
public record ResultVersionRecord(
    UUID id,
    UUID resultSeriesId,
    int versionNo,
    String status,
    UUID correctsVersionId,
    String correctionReason,
    String entrySource,
    String findings,
    String conclusion,
    String structuredPayload,
    UUID authoredBy,
    UUID verifiedBy,
    UUID finalizedBy,
    Instant createdAt,
    Instant verifiedAt,
    Instant finalizedAt,
    long rowVersion) {}
