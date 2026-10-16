package com.ngockhanh.clinic.catalog.infrastructure.persistence.view;

import java.time.Instant;
import java.util.UUID;

/** SQL projection for catalog management; kept inside the persistence adapter. */
public record MedicineDetailView(
    UUID id,
    String code,
    String genericName,
    String brandName,
    String strength,
    String dosageForm,
    String unit,
    boolean active,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt) {}
