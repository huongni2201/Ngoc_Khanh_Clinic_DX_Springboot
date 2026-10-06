package com.ngockhanh.clinic.accesscontrol.domain.valueobject;

import java.util.List;
import java.util.UUID;

/** Active role granted to an account, with the permission codes the role currently carries. */
public record RoleGrant(UUID roleId, String roleCode, List<String> permissions) {
  public RoleGrant {
    if (roleId == null || roleCode == null || roleCode.isBlank())
      throw new IllegalArgumentException("Role identity is required");
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }
}
