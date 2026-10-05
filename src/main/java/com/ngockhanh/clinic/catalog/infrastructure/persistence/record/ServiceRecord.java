package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.services}. */
public record ServiceRecord(
    UUID id,
    String code,
    String name,
    String serviceType,
    UUID performingDepartmentId,
    UUID specialtyId,
    BigDecimal unitPrice,
    String preparationInstruction,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
