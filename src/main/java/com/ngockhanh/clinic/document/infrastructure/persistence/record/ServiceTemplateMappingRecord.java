package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record ServiceTemplateMappingRecord(
        UUID id,
        UUID serviceId,
        UUID documentTemplateId,
        Integer displayOrder,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
