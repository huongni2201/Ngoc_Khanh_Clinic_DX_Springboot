package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** A row in public.health_examination_records. */
public record HealthExaminationRecordRecord(
    UUID id,
    UUID batchParticipantId,
    UUID encounterId,
    String mrn,
    String status,
    Instant preparedAt,
    Instant issuedAt,
    Instant finalizedAt,
    Instant cancelledAt,
    String cancelReason,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
