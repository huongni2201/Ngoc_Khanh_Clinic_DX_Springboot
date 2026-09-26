package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ServicePriceRecord(
        UUID id,
        UUID serviceId,
        String priceType,
        String payerReference,
        BigDecimal amount,
        String currency,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        Boolean isActive
) {
}
