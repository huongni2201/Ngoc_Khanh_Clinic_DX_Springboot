package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record EncounterAssignmentRecord(
        UUID id,
        UUID encounterId,
        UUID departmentId,
        UUID roomId,
        UUID doctorStaffId,
        Instant assignedAt,
        Instant endedAt,
        UUID assignedByUserId,
        String destinationLabel
) {
}
