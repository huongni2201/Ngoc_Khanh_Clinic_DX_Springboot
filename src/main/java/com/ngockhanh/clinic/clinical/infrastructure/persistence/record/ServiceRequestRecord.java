package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        LocalDateTime orderedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
