package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceRequestRecord(
        UUID id,
        UUID orderRoundId,
        UUID serviceId,
        String status,
        String priority,
        UUID performingDepartmentId,
        UUID performingRoomId,
        String performingLocationLabel,
        String serviceNameSnapshot,
        BigDecimal unitPriceSnapshot,
        String preparationInstructionsSnapshot,
        OffsetDateTime orderedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
