package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record IntegrationEndpointRecord(
        UUID id,
        String endpointCode,
        String integrationType,
        String endpointName,
        String baseUrl,
        String credentialReference,
        String configJson,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
