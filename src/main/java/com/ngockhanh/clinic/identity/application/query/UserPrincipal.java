package com.ngockhanh.clinic.identity.application.query;

import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("access")
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
                    new Assignment(
                        assignment.roleId(),
                        assignment.roleCode(),
                        assignment.permissions(),
                        assignment.grantedBy(),
                        assignment.grantedAt()))
            .toList();
    return new UserPrincipal(
        userId,
        staffId,
        patientId,
        username,
        principalType,
        roleGrants,
        idleExpiresAt,
        absoluteExpiresAt);
  }

  public record Assignment(
      UUID roleId, String roleCode, List<String> permissions, UUID grantedBy, Instant grantedAt) {
    public Assignment {
      permissions = List.copyOf(permissions);
    }
  }
}
