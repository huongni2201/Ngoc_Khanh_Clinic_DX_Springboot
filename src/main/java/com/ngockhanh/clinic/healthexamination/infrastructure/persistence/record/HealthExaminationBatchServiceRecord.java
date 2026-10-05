package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record HealthExaminationBatchServiceRecord(
    UUID id,
    UUID batchId,
    UUID serviceId,
    BigDecimal referencePriceSnapshot,
    BigDecimal negotiatedPrice,
    int displayOrder,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
