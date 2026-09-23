package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record EncounterRecord(
        UUID id,
        String encounterCode,
        UUID patientId,
        String encounterType,
        String reason,
        String priority,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] rowVersion,
        LocalDateTime preparedAt,
        LocalDateTime checkedInAt,
        Long checkedInByUserId
) {
}
