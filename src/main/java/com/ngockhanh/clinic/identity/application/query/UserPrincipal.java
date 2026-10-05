package com.ngockhanh.clinic.identity.application.query;

import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@org.springframework.modulith.NamedInterface("access")
@Builder
public record UserPrincipal(
    UUID userId,
    UUID staffId,
    UUID patientId,
    String username,
    String principalType,
    List<Assignment> roleAssignments,
    Instant idleExpiresAt,
    Instant absoluteExpiresAt) {
  public UserPrincipal {
    roleAssignments = List.copyOf(roleAssignments);
  }

  public static UserPrincipal from(
      UUID userId,
      UUID staffId,
      UUID patientId,
      String username,
      String principalType,
      List<RoleAssignment> assignments,
      Instant idleExpiresAt,
      Instant absoluteExpiresAt) {
    List<Assignment> roleGrants =
        assignments.stream()
            .map(
                assignment ->
                    Assignment.builder()
                        .roleId(assignment.roleId())
                        .roleCode(assignment.roleCode())
                        .permissions(assignment.permissions())
                        .grantedBy(assignment.grantedBy())
                        .grantedAt(assignment.grantedAt())
                        .build())
            .toList();
    return UserPrincipal.builder()
        .userId(userId)
        .staffId(staffId)
        .patientId(patientId)
        .username(username)
        .principalType(principalType)
        .roleAssignments(roleGrants)
        .idleExpiresAt(idleExpiresAt)
        .absoluteExpiresAt(absoluteExpiresAt)
        .build();
  }

  @Builder
  public record Assignment(
      UUID roleId, String roleCode, List<String> permissions, UUID grantedBy, Instant grantedAt) {
    public Assignment {
      permissions = List.copyOf(permissions);
    }
  }
}
