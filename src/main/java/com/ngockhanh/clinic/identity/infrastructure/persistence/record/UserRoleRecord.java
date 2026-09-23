package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record UserRoleRecord(
        UUID id,
        UUID userId,
        UUID roleId,
        UUID departmentId,
        UUID roomId,
        LocalDateTime validFrom,
        LocalDateTime validTo
) {
}
