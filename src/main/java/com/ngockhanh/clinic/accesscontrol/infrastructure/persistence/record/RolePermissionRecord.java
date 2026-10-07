package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.role_permissions}. */
@Builder
public record RolePermissionRecord(UUID roleId, UUID permissionId) {}
