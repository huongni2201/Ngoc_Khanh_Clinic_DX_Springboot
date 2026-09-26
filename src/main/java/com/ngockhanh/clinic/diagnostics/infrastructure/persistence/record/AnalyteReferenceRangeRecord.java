package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.Instant;

public record AnalyteReferenceRangeRecord(
        UUID id,
        UUID analyteId,
        String sex,
        Integer ageMinDays,
        Integer ageMaxDays,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        String textRange,
        BigDecimal warningLower,
        BigDecimal warningUpper,
        Instant effectiveFrom,
        Instant effectiveTo
) {
}
