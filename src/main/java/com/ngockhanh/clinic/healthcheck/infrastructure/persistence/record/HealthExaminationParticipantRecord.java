package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
