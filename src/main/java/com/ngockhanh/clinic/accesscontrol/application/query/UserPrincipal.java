package com.ngockhanh.clinic.accesscontrol.application.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Authenticated account restored from its server-side session.
 *
 * <p>Exactly one of {@code staffId} and {@code patientId} identifies the account owner, according
 * to {@code principalType}. Role assignments are the snapshot taken at login and are not refreshed
 * by later database changes.
 */
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
    roleAssignments = roleAssignments == null ? List.of() : List.copyOf(roleAssignments);
  }

  /** Active role granted to the account, with the permission codes it carried at login. */
  @Builder
  public record Assignment(UUID roleId, String roleCode, List<String> permissions) {
    public Assignment {
      permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
  }
}
