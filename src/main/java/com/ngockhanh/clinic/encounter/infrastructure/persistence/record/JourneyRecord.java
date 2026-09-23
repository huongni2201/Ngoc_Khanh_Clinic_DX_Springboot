package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record JourneyRecord(
        UUID id,
        UUID encounterId,
        String currentStage,
        UUID currentDepartmentId,
        UUID currentRoomId,
        LocalDateTime stageEnteredAt,
        LocalDateTime updatedAt,
        byte[] rowVersion
) {
}
