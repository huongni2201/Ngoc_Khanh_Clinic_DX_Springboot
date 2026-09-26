package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record EncounterRecord(
        UUID id,
        String encounterCode,
        UUID patientId,
        String encounterType,
        String reason,
        String priority,
        String status,
        Instant startedAt,
        Instant completedAt,
        Instant canceledAt,
        Instant createdAt,
        Instant updatedAt,
        long rowVersion,
        Instant preparedAt,
        Instant checkedInAt,
        UUID checkedInByUserId
) {
}
