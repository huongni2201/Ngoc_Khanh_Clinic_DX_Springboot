package com.ngockhanh.clinic.healthcheck.application.query;

import java.time.Instant;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;

public record ParticipantSummary(
        UUID batchParticipantId,
        UUID participantId,
        String participantCode,
        String departmentName,
        String jobTitle,
        String occupation,
        AdministrativeSnapshot snapshot,
        String status,
        Instant createdAt) {
}
