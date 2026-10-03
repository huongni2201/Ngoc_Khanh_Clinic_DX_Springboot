package com.ngockhanh.clinic.identity.domain.entity;

import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import java.util.List;
import java.util.UUID;

public record UserAccount(
    UUID userId,
    UUID staffId,
    UUID patientId,
    String username,
    String password,
    String status,
    String principalType,
    boolean staffActive,
    List<RoleAssignment> roles) {
  public UserAccount {
    roles = List.copyOf(roles);
  }

  public boolean eligible() {
    return "ACTIVE".equals(status)
        && switch (principalType) {
          case "STAFF" -> staffId != null && patientId == null && staffActive;
          case "PATIENT" -> patientId != null && staffId == null;
          case null, default -> false;
        };
  }

  @Override
  public String toString() {
    return "UserAccount[userId=" + userId + "]";
  }
}
