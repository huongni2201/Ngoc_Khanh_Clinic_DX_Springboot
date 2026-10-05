package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.lab_analytes}. */
public record LabAnalyteRecord(
    UUID id, String code, String name, String defaultUnit, String valueType, boolean active) {}
