package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A row in public.health_examination_participant_services. */
public record HealthExaminationParticipantServiceRecord(
    UUID id,
    UUID batchId,
    UUID batchParticipantId,
    UUID batchServiceId,
    boolean isPerformed,
    UUID serviceRequestId,
    BigDecimal unitPriceSnapshot,
    UUID recordedBy,
    Instant recordedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
