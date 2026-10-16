package com.ngockhanh.clinic.catalog.infrastructure.persistence.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** SQL projection for catalog management; kept inside the persistence adapter. */
public record ManagedServiceItemView(
    UUID id,
    String code,
    String name,
    String serviceType,
    UUID performingDepartmentId,
    String performingDepartmentName,
    UUID specialtyId,
    String specialtyName,
    BigDecimal unitPrice,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {}
