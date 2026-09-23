package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServicePriceRecord(
        UUID id,
        UUID serviceId,
        String priceType,
        String payerReference,
        BigDecimal amount,
        String currency,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Boolean isActive
) {
}
