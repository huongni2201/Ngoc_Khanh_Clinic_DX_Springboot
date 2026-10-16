package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.math.BigDecimal;
import java.util.UUID;

/** SQL projection of performed rows that share an agreement service and a price snapshot. */
public record ServiceAmountView(
    UUID agreementServiceId, BigDecimal unitPrice, long examinedCount) {}
