package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record IdempotencyKeyRecord(
        UUID id,
        String scope,
        String idempotencyKey,
        String requestHash,
        String resultEntityType,
        String resultEntityId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
