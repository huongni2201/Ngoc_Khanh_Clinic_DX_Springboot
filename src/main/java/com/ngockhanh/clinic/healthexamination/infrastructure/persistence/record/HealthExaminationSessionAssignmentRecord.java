package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** A row in public.health_examination_session_assignments. */
@Builder
public record HealthExaminationSessionAssignmentRecord(
    UUID id,
    UUID organizationId,
    UUID participantId,
    UUID sessionId,
    String status,
    UUID replacedByAssignmentId,
    Instant attendedAt,
    String note,
    UUID recordedBy,
    Instant recordedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
