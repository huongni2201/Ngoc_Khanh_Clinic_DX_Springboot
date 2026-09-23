package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserRecord(
        UUID id,
        String principalType,
        UUID staffId,
        UUID patientId,
        String authProvider,
        String authSubject,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
