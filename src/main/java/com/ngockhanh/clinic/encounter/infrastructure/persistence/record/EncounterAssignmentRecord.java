package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record EncounterAssignmentRecord(
        UUID id,
        UUID encounterId,
        UUID departmentId,
        UUID roomId,
        UUID doctorStaffId,
        LocalDateTime assignedAt,
        LocalDateTime endedAt,
        UUID assignedByUserId,
        String destinationLabel
) {
}
