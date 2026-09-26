package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EncounterRecord(
        UUID id,
        String encounterCode,
        UUID patientId,
        String encounterType,
        String reason,
        String priority,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime canceledAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long rowVersion,
        OffsetDateTime preparedAt,
        OffsetDateTime checkedInAt,
        UUID checkedInByUserId
) {
}
