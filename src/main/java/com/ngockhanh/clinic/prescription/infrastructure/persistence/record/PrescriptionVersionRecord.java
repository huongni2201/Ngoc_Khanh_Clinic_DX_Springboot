package com.ngockhanh.clinic.prescription.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.prescription_versions}. */
@Builder
public record PrescriptionVersionRecord(
    UUID id,
    UUID prescriptionId,
    int versionNo,
    String status,
    UUID correctsVersionId,
    String correctionReason,
    UUID authoredBy,
    UUID issuedBy,
    Instant issuedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
