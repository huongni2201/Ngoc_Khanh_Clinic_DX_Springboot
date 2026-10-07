package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.roles}. */
@Builder
public record RoleRecord(UUID id, String code, String name, String description, boolean active) {}
