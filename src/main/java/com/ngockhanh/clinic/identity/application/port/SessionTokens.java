package com.ngockhanh.clinic.identity.application.port;

import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SessionTokens {
  record Claims(UUID userId, UUID staffId, String username, UUID tokenId,
                Instant issuedAt, Instant expiresAt, List<RoleAssignment> roles) {
    public Claims {
      roles = List.copyOf(roles);
    }
  }

  String issue(Claims claims);

  Claims verify(String jwt);
}
