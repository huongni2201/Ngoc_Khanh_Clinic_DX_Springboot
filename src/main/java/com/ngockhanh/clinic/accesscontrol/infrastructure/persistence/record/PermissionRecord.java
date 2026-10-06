package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.permissions}. */
public record PermissionRecord(UUID id, String code, String name, String description) {}
