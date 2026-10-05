package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.lab_test_analytes}. */
@Builder
public record LabTestAnalyteRecord(
    UUID labTestId, UUID analyteId, int displayOrder, boolean required) {}
