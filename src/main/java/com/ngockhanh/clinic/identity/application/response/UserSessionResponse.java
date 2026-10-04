package com.ngockhanh.clinic.identity.application.response;

import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserSessionResponse(
    UUID accountId,
    UUID staffMemberId,
    UUID patientId,
    String username,
    String accountType,
    List<RoleAssignmentResponse> roleAssignments,
    Instant idleExpiresAt,
    Instant absoluteExpiresAt) {
  public UserSessionResponse {
    roleAssignments = List.copyOf(roleAssignments);
  }

  public static UserSessionResponse from(UserPrincipal principal) {
    return new UserSessionResponse(
        principal.userId(),
        principal.staffId(),
        principal.patientId(),
        principal.username(),
        principal.principalType(),
        principal.roleAssignments().stream()
            .map(
                r ->
                    new RoleAssignmentResponse(
                        r.roleId(), r.roleCode(), r.permissions(), r.grantedBy(), r.grantedAt()))
            .toList(),
        principal.idleExpiresAt(),
        principal.absoluteExpiresAt());
  }

  public record RoleAssignmentResponse(
      UUID roleId, String roleCode, List<String> permissions, UUID grantedBy, Instant grantedAt) {
    public RoleAssignmentResponse {
      permissions = List.copyOf(permissions);
    }
  }
}
