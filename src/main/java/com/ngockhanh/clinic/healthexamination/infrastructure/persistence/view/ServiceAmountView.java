package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.math.BigDecimal;
import java.util.UUID;

/** SQL projection of performed rows that share a batch service and a price snapshot. */
public record ServiceAmountView(UUID batchServiceId, BigDecimal unitPrice, long examinedCount) {}
