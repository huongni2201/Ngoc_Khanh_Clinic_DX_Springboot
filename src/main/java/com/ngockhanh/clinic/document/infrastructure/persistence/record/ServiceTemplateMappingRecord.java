package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record ServiceTemplateMappingRecord(
        UUID id,
        UUID serviceId,
        UUID documentTemplateId,
        Integer displayOrder,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
