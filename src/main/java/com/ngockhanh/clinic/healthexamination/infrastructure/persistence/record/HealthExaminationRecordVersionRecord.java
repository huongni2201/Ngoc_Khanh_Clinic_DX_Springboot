package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** A row in public.health_examination_record_versions. */
@Builder
public record HealthExaminationRecordVersionRecord(
    UUID id,
    UUID healthExaminationRecordId,
    int versionNo,
    UUID administrativeSnapshotId,
    String status,
    String conclusionText,
    UUID conclusionBy,
    Instant conclusionAt,
    UUID correctsVersionId,
    String correctionReason,
    UUID completedBy,
    Instant completedAt,
    UUID issuedBy,
    Instant issuedAt,
    UUID authoredBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
