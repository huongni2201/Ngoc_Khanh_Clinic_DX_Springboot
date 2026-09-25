package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HealthExaminationBatchEmployeeServiceRecord(
        UUID id,
        UUID healthExaminationBatchEmployeeId,
        UUID healthExaminationBatchServiceId,
        UUID serviceRequestId,
        boolean billable,
        BigDecimal unitPriceSnapshot,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
