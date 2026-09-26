package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
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
        Instant receivedAt,
        Instant processedAt,
        Integer retryCount
) {
}
