package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HealthCheckBatchEmployeeServiceRecord(
        UUID id,
        UUID healthCheckBatchEmployeeId,
        UUID healthCheckBatchServiceId,
        UUID serviceRequestId,
        Boolean billable,
        BigDecimal unitPriceSnapshot,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
