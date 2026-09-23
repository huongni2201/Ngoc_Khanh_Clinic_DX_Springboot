package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

public record RolePermissionRecord(
        UUID id,
        UUID roleId,
        UUID permissionId
) {
}
