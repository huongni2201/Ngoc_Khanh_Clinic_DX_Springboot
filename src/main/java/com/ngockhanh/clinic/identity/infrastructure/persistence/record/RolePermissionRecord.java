package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

@Builder
public record RolePermissionRecord(UUID roleId, UUID permissionId) {}
