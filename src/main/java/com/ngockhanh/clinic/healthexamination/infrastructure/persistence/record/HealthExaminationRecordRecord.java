package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** A row in public.health_examination_records. */
@Builder
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
