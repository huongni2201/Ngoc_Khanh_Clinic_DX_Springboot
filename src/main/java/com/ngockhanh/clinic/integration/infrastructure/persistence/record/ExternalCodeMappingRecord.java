package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record ExternalCodeMappingRecord(
        UUID id,
        UUID integrationEndpointId,
        String mappingType,
        String externalCode,
        String internalEntityType,
        UUID internalEntityId,
        Instant validFrom,
        Instant validTo
) {
}
