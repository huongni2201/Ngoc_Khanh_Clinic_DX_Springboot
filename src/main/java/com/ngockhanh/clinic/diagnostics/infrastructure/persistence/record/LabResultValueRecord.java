package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LabResultValueRecord(
        UUID id,
        UUID labResultId,
        UUID analyteId,
        BigDecimal numericValue,
        String textValue,
        String unitSnapshot,
        String referenceRangeSnapshot,
        String abnormalFlag,
        String instrumentCode,
        OffsetDateTime measuredAt
) {
}
