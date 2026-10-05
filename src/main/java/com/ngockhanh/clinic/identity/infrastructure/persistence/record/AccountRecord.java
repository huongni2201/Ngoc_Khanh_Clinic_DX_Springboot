package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AccountRecord(
    UUID id,
    String accountType,
    String username,
    String passwordHash,
    UUID staffMemberId,
    UUID patientId,
    String status,
    byte[] refreshTokenHash,
    Instant refreshTokenExpiresAt,
    Instant refreshTokenRevokedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {
  @Override
  public String toString() {
    return "AccountRecord[id=" + id + "]";
  }
}
