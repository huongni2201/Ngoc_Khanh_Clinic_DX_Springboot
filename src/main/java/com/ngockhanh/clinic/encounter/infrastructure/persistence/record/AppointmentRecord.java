package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentRecord(
        UUID id,
        UUID patientId,
        UUID sourceEncounterId,
        UUID departmentId,
        UUID doctorStaffId,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        String status,
        String reason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
