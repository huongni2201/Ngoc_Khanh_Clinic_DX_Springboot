package com.ngockhanh.clinic.identity.domain.entity;

import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import java.util.List;
import java.util.UUID;

public record UserAccount(
    UUID accountId,
    UUID staffMemberId,
    UUID patientId,
    String username,
    String passwordHash,
    String status,
    String accountType,
    String staffStatus,
    List<RoleAssignment> roles) {
  public UserAccount {
    roles = List.copyOf(roles);
  }

  public boolean eligible() {
    return "ACTIVE".equals(status)
        && switch (accountType) {
          case "STAFF" ->
              staffMemberId != null && patientId == null && "ACTIVE".equals(staffStatus);
          case "PATIENT" -> patientId != null && staffMemberId == null;
          case null, default -> false;
        };
  }

  @Override
  public String toString() {
    return "UserAccount[accountId=" + accountId + "]";
  }
}
