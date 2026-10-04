package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.specialties}. */
public record SpecialtyRecord(UUID id, String code, String name, boolean active) {}
