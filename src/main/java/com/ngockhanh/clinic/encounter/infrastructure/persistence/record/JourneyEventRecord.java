package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record JourneyEventRecord(
        UUID id,
        UUID journeyId,
        String fromStage,
        String toStage,
        UUID departmentId,
        UUID roomId,
        String reason,
        LocalDateTime occurredAt,
        Long actorUserId
) {
}
