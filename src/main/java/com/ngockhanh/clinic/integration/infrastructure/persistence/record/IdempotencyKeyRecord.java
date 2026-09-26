package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record IdempotencyKeyRecord(
        UUID id,
        String scope,
        String idempotencyKey,
        String requestHash,
        String resultEntityType,
        String resultEntityId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
}
