package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record BatchParticipantSummaryRecord(
        UUID batchParticipantId,
        UUID participantId,
        String participantCode,
        String departmentName,
        String jobTitle,
        String occupation,
        String administrativeSnapshotJson,
        String status,
        Instant createdAt) {
}
