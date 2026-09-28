package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record UserRecord(
    UUID id,
    String principalType,
    UUID staffId,
    UUID patientId,
    String status,
    Instant lastLoginAt,
    Instant createdAt,
    String username,
    String password
) {
  @Override
  public String toString() {
    return "UserRecord[id=" + id + ", principalType=" + principalType + ", status=" + status + "]";
  }
}
