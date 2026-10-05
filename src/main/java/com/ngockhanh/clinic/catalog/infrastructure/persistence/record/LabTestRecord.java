package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.lab_tests}. */
public record LabTestRecord(UUID id, UUID serviceId, String code, String name, boolean active) {}
