package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEventRecord(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        UUID correlationId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt,
        Integer retryCount
) {
}
