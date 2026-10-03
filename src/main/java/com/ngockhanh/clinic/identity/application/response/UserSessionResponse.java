package com.ngockhanh.clinic.identity.application.response;

import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserSessionResponse(
    UUID userId,
    UUID staffId,
    UUID patientId,
    String username,
    String principalType,
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
                        r.assignmentId(),
                        r.roleCode(),
                        r.permissions(),
                        r.departmentId(),
                        r.roomId(),
                        r.validFrom(),
                        r.validTo()))
            .toList(),
        principal.idleExpiresAt(),
        principal.absoluteExpiresAt());
  }

  public record RoleAssignmentResponse(
      UUID assignmentId,
      String roleCode,
      List<String> permissions,
      UUID departmentId,
      UUID roomId,
      Instant validFrom,
      Instant validTo) {
    public RoleAssignmentResponse {
      permissions = List.copyOf(permissions);
    }
  }
}
