package com.ngockhanh.clinic.identity.application.response;

import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
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
    return UserSessionResponse.builder()
        .accountId(principal.userId())
        .staffMemberId(principal.staffId())
        .patientId(principal.patientId())
        .username(principal.username())
        .accountType(principal.principalType())
        .roleAssignments(
            principal.roleAssignments().stream()
                .map(
                    r ->
                        RoleAssignmentResponse.builder()
                            .roleId(r.roleId())
                            .roleCode(r.roleCode())
                            .permissions(r.permissions())
                            .grantedBy(r.grantedBy())
                            .grantedAt(r.grantedAt())
                            .build())
                .toList())
        .idleExpiresAt(principal.idleExpiresAt())
        .absoluteExpiresAt(principal.absoluteExpiresAt())
        .build();
  }

  @Builder
  public record RoleAssignmentResponse(
      UUID roleId, String roleCode, List<String> permissions, UUID grantedBy, Instant grantedAt) {
    public RoleAssignmentResponse {
      permissions = List.copyOf(permissions);
    }
  }
}
