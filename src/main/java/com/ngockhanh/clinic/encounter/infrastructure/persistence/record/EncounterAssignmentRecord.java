package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record EncounterAssignmentRecord(
        UUID id,
        UUID encounterId,
        UUID departmentId,
        UUID roomId,
        UUID doctorStaffId,
        OffsetDateTime assignedAt,
        OffsetDateTime endedAt,
        UUID assignedByUserId,
        String destinationLabel
) {
}
