package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record ExternalCodeMappingRecord(
        UUID id,
        UUID integrationEndpointId,
        String mappingType,
        String externalCode,
        String internalEntityType,
        UUID internalEntityId,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
) {
}
