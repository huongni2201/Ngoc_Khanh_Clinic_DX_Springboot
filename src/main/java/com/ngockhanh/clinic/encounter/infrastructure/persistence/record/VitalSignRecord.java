package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VitalSignRecord(
        UUID id,
        UUID encounterId,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal bmi,
        Integer pulseBpm,
        Integer systolicBp,
        Integer diastolicBp,
        String physicalClassification,
        LocalDateTime measuredAt,
        Long recordedByStaffId
) {
}
