package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record ServiceRecord(
        UUID id,
        String serviceCode,
        String serviceName,
        String serviceType,
        UUID performingDepartmentId,
        UUID defaultRoomId,
        Boolean requiresPayment,
        Boolean requiresSpecimen,
        Boolean healthExaminationEligible,
        String resultType,
        UUID labPanelId,
        String preparationInstructions,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
