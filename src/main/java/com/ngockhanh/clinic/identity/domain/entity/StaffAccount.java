package com.ngockhanh.clinic.identity.domain.entity;

import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;

import java.util.List;
import java.util.UUID;

public record StaffAccount(UUID userId, UUID staffId, String username, String password, String status,
                           String principalType, boolean staffActive, List<RoleAssignment> roles) {
  public StaffAccount {
    roles = List.copyOf(roles);
  }

  public boolean eligible() {
    return "ACTIVE".equals(status) && "STAFF".equals(principalType) && staffId != null && staffActive && !roles.isEmpty();
  }

  @Override
  public String toString() {
    return "StaffAccount[userId=" + userId + "]";
  }
}
