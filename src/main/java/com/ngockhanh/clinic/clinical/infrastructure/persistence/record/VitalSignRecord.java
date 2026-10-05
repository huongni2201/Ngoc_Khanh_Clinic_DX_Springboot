package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.vital_signs}. */
@Builder
public record VitalSignRecord(
    UUID id,
    UUID encounterId,
    Instant measuredAt,
    BigDecimal heightCm,
    BigDecimal weightKg,
    BigDecimal temperatureC,
    Integer pulseBpm,
    Integer respiratoryRate,
    Integer systolicBp,
    Integer diastolicBp,
    UUID recordedBy) {}
