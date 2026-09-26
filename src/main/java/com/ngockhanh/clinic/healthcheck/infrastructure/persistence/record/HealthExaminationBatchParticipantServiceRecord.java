package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HealthExaminationBatchParticipantServiceRecord(
        UUID id,
        UUID healthExaminationBatchParticipantId,
        UUID healthExaminationBatchServiceId,
        UUID serviceRequestId,
        boolean billable,
        BigDecimal unitPriceSnapshot,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
