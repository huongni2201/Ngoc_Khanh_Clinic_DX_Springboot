package com.ngockhanh.clinic.accesscontrol.domain.valueobject;

import java.util.UUID;

/** Active role chosen for a grant, identified by its ID and code. */
public record RoleRef(UUID id, String code) {
  public RoleRef {
    if (id == null || code == null || code.isBlank())
      throw new IllegalArgumentException("Role id and code are required");
  }
}
