package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record RoleGrantRow(UUID assignmentId, String roleCode, UUID departmentId, UUID roomId,
                           Instant validFrom, Instant validTo, String permissionCode) {
}
