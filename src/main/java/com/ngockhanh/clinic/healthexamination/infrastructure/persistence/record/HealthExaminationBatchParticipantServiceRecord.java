package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HealthExaminationBatchParticipantServiceRecord(
        UUID id,
        UUID healthExaminationBatchParticipantId,
        UUID healthExaminationBatchServiceId,
        UUID serviceRequestId,
        boolean billable,
        BigDecimal unitPriceSnapshot,
        Instant createdAt,
        Instant updatedAt
) {
}
