package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.roles}. */
public record RoleRecord(UUID id, String code, String name, String description, boolean active) {}
