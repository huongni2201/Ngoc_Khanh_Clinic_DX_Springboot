package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.issued_representations}. */
public record IssuedRepresentationRecord(
    UUID id,
    String documentType,
    UUID healthExaminationRecordSnapshotId,
    UUID healthExaminationRecordVersionId,
    UUID resultVersionId,
    UUID prescriptionVersionId,
    UUID assessmentVersionId,
    UUID templateVersionId,
    UUID issuedBy,
    Instant issuedAt) {}
