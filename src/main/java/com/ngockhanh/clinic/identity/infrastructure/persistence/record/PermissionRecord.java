package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

public record PermissionRecord(
        UUID id,
        String permissionCode,
        String module,
        String description
) {
}
