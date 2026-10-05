package com.ngockhanh.clinic.identity.infrastructure.persistence.projection;

import java.time.Instant;
import java.util.UUID;

public record RoleGrantRow(
    UUID roleId, String roleCode, UUID grantedBy, Instant grantedAt, String permissionCode) {}
