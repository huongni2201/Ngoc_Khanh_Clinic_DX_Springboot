package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record;

import java.util.UUID;

/** Persistence row of {@code public.role_permissions}. */
public record RolePermissionRecord(UUID roleId, UUID permissionId) {}
