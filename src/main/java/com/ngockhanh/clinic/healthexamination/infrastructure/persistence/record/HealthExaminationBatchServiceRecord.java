package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HealthExaminationBatchServiceRecord(
    UUID id,
    UUID healthExaminationBatchId,
    UUID serviceId,
    UUID documentTemplateVersionId,
    String serviceCodeSnapshot,
    String serviceNameSnapshot,
    BigDecimal negotiatedUnitPrice,
    String currency,
    Integer displayOrder,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
