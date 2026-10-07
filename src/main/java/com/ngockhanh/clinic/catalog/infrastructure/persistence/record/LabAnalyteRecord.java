package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.lab_analytes}. */
@Builder
public record LabAnalyteRecord(
    UUID id, String code, String name, String defaultUnit, String valueType, boolean active) {}
