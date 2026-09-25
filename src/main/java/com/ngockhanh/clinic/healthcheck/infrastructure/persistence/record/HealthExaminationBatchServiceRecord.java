package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HealthExaminationBatchServiceRecord(
        UUID id,
        UUID healthExaminationBatchId,
        UUID serviceId,
        UUID documentTemplateVersionId,
        String serviceCodeSnapshot,
        String serviceNameSnapshot,
        BigDecimal basePriceSnapshot,
        BigDecimal negotiatedUnitPrice,
        String currency,
        Integer displayOrder,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
