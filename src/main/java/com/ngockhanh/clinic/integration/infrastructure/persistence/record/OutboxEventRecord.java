package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxEventRecord(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        UUID correlationId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        Integer retryCount
) {
}
