package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.specialties}. */
@Builder
public record SpecialtyRecord(UUID id, String code, String name, boolean active) {}
