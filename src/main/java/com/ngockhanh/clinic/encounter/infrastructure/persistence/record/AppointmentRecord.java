package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentRecord(
        UUID id,
        UUID patientId,
        UUID sourceEncounterId,
        UUID departmentId,
        UUID doctorStaffId,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        String status,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
