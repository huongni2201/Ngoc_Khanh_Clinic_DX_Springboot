package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.permissions}. */
@Builder
public record PermissionRecord(UUID id, String code, String name, String description) {}
