package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo
) {
}
