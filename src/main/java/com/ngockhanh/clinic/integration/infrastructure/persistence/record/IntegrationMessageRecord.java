package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record IntegrationMessageRecord(
        UUID id,
        UUID integrationEndpointId,
        String direction,
        String messageType,
        String externalMessageId,
        UUID correlationId,
        String status,
        String payloadReference,
        String payloadHash,
        String errorMessage,
        LocalDateTime receivedAt,
        LocalDateTime processedAt,
        Integer retryCount
) {
}
