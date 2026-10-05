package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.medicines}. */
public record MedicineRecord(
    UUID id,
    String code,
    String genericName,
    String brandName,
    String strength,
    String dosageForm,
    String unit,
    boolean active) {}
