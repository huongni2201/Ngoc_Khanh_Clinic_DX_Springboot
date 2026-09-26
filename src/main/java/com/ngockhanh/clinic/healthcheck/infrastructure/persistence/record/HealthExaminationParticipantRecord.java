package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record HealthExaminationParticipantRecord(
        UUID id,
        UUID organizationId,
        UUID patientId,
        String participantCode,
        String identificationNumber,
        String fullName,
        LocalDate dateOfBirth,
        String sex,
        String departmentName,
        String jobTitle,
        String occupation,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
