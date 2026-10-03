package com.ngockhanh.clinic.identity.infrastructure.persistence.projection;

import java.util.UUID;

public record UserLoginRow(
    UUID userId,
    UUID staffId,
    UUID patientId,
    String username,
    String password,
    String status,
    String principalType,
    boolean staffActive) {
  @Override
  public String toString() {
    return "UserLoginRow[userId=" + userId + "]";
  }
}
