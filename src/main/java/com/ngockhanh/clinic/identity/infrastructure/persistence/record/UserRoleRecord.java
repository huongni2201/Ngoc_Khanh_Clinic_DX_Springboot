package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record UserRoleRecord(
        UUID id,
        UUID userId,
        UUID roleId,
        UUID departmentId,
        UUID roomId,
        Instant validFrom,
        Instant validTo
) {
}
