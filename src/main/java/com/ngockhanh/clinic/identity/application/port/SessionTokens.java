package com.ngockhanh.clinic.identity.application.port;

import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionTokens {
  record Claims(
      UUID userId,
      UUID staffId,
      UUID patientId,
      String username,
      String principalType,
      UUID tokenId,
      Instant issuedAt,
      Instant expiresAt,
      List<RoleAssignment> roles) {
    public Claims {
      roles = List.copyOf(roles);
    }
  }

  String issue(Claims claims);

  Optional<Claims> verify(String jwt);
}
