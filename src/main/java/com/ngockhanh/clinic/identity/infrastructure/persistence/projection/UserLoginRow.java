package com.ngockhanh.clinic.identity.infrastructure.persistence.projection;

import java.util.UUID;

public record UserLoginRow(
    UUID accountId,
    UUID staffMemberId,
    UUID patientId,
    String username,
    String passwordHash,
    String status,
    String accountType,
    String staffStatus) {
  @Override
  public String toString() {
    return "UserLoginRow[accountId=" + accountId + "]";
  }
}
