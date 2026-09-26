package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record UserRoleRecord(
        UUID id,
        UUID userId,
        UUID roleId,
        UUID departmentId,
        UUID roomId,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
) {
}
