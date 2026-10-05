package com.ngockhanh.clinic.identity.domain.valueobject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleAssignment(
    UUID roleId, String roleCode, List<String> permissions, UUID grantedBy, Instant grantedAt) {
  public RoleAssignment {
    if (roleId == null
        || roleCode == null
        || roleCode.isBlank()
        || grantedBy == null
        || grantedAt == null
        || permissions == null
        || permissions.stream().anyMatch(p -> p == null || p.isBlank())) {
      throw new IllegalArgumentException("Invalid role assignment");
    }
    permissions = permissions.stream().distinct().sorted().toList();
  }
}
