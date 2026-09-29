package com.ngockhanh.clinic.identity.application.response;

import com.ngockhanh.clinic.identity.application.query.access.StaffPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffSessionResponse(UUID userId, UUID staffId, String username, String principalType,
                                   List<RoleAssignmentResponse> roleAssignments,
                                   Instant idleExpiresAt, Instant absoluteExpiresAt) {
  public StaffSessionResponse {
    roleAssignments = List.copyOf(roleAssignments);
  }

  public static StaffSessionResponse from(StaffPrincipal principal) {
    return new StaffSessionResponse(principal.userId(), principal.staffId(), principal.username(),
        principal.principalType(), principal.roleAssignments().stream()
        .map(r -> new RoleAssignmentResponse(r.assignmentId(), r.roleCode(), r.permissions(),
            r.departmentId(), r.roomId(), r.validFrom(), r.validTo())).toList(),
        principal.idleExpiresAt(), principal.absoluteExpiresAt());
  }

  public record RoleAssignmentResponse(UUID assignmentId, String roleCode, List<String> permissions,
                                       UUID departmentId, UUID roomId, Instant validFrom, Instant validTo) {
    public RoleAssignmentResponse {
      permissions = List.copyOf(permissions);
    }
  }
}
