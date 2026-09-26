package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record AppointmentRecord(
        UUID id,
        UUID patientId,
        UUID sourceEncounterId,
        UUID departmentId,
        UUID doctorStaffId,
        Instant scheduledStart,
        Instant scheduledEnd,
        String status,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
}
