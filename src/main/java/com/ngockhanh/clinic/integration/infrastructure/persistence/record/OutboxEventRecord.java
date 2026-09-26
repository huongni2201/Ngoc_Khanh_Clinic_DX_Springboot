package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventRecord(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        UUID correlationId,
        String status,
        Instant createdAt,
        Instant publishedAt,
        Integer retryCount
) {
}
