package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record IntegrationEndpointRecord(
        UUID id,
        String endpointCode,
        String integrationType,
        String endpointName,
        String baseUrl,
        String credentialReference,
        String configJson,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
