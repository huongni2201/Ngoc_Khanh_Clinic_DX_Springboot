package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HealthExaminationBatchParticipantRecord(
        UUID id,
        UUID healthExaminationBatchId,
        UUID healthExaminationParticipantId,
        String participantCodeSnapshot,
        String departmentSnapshot,
        String jobTitleSnapshot,
        String occupationSnapshot,
        String administrativeSnapshotJson,
        String status,
        OffsetDateTime createdAt
) {
}
