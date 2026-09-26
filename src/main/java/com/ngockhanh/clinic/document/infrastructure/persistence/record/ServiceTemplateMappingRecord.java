package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record ServiceTemplateMappingRecord(
        UUID id,
        UUID serviceId,
        UUID documentTemplateId,
        Integer displayOrder,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
