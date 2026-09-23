package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

public record RoleRecord(
        UUID id,
        String roleCode,
        String roleName,
        Boolean isActive
) {
}
