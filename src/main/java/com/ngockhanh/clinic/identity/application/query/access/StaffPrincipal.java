package com.ngockhanh.clinic.identity.application.query.access;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffPrincipal(UUID userId, UUID staffId, String username, String principalType,
                             List<Assignment> roleAssignments, Instant idleExpiresAt, Instant absoluteExpiresAt) {
  public StaffPrincipal {
    roleAssignments = List.copyOf(roleAssignments);
  }

  public record Assignment(UUID assignmentId, String roleCode, List<String> permissions,
                           UUID departmentId, UUID roomId, Instant validFrom, Instant validTo) {
    public Assignment {
      permissions = List.copyOf(permissions);
    }
  }
}
