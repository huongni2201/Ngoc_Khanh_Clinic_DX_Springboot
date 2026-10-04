package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

public record RolePermissionRecord(UUID roleId, UUID permissionId) {}
