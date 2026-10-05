package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.medicines}. */
@Builder
public record MedicineRecord(
    UUID id,
    String code,
    String genericName,
    String brandName,
    String strength,
    String dosageForm,
    String unit,
    boolean active) {}
