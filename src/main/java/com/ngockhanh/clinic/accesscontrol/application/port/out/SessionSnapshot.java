package com.ngockhanh.clinic.accesscontrol.application.port.out;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Server-side session state stored per session. Kept separate from the published {@link
 * UserPrincipal} so that changing the public contract does not break stored sessions.
 */
@Builder
public record SessionSnapshot(
    UUID accountId,
    String accountType,
    UUID staffMemberId,
    UUID patientId,
    String username,
    List<Role> roles,
    Instant createdAt,
    Instant absoluteExpiresAt) {

  public SessionSnapshot {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }

  /** Role and permission codes captured at sign-in. */
  @Builder
  public record Role(UUID roleId, String roleCode, List<String> permissions) {
    public Role {
      permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
  }

  /** Principal for this session, valid until {@code idleExpiresAt} unless used again. */
  public UserPrincipal toPrincipal(Instant idleExpiresAt) {
    return UserPrincipal.builder()
        .userId(accountId)
        .staffId(staffMemberId)
        .patientId(patientId)
        .username(username)
        .principalType(accountType)
        .roleAssignments(
            roles.stream()
                .map(
                    role ->
                        UserPrincipal.Assignment.builder()
                            .roleId(role.roleId())
                            .roleCode(role.roleCode())
                            .permissions(role.permissions())
                            .build())
                .toList())
        .idleExpiresAt(idleExpiresAt)
        .absoluteExpiresAt(absoluteExpiresAt)
        .build();
  }
}
