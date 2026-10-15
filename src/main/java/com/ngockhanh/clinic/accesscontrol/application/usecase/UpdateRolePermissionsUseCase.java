package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRolePermissionsCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.RolePermissionsResponse;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.exception.ProtectedRoleException;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants permissions to and removes permissions from one active role in a single transaction (SRS
 * UC-009 normal flow, 1.3.4 Save Permissions). The role row version guards against changes made by
 * another user since the caller read the role (5-EX-02). Accounts holding the role receive the new
 * permissions from their next sign-in. The grants and the audit event are written together.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateRolePermissionsUseCase {
  static final String UPDATE_ROLE_PERMISSIONS = "UPDATE_ROLE_PERMISSIONS";
  public static final String ASSIGN_PERMISSION = "ROLE_PERMISSION_ASSIGN";

  private final RoleRepository roles;
  private final AuditWriter audit;

  /**
   * Applies the pending grants and removals.
   *
   * @param roleId role identifier
   * @param command expected row version and the permission codes to grant and to remove
   * @param principal authenticated caller, recorded as the audit actor
   * @return the permissions now granted and the incremented row version
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code
   *     ROLE_PERMISSION_ASSIGN}
   * @throws IllegalArgumentException when no code is given or a code is both granted and removed
   * @throws ResourceNotFoundException when the role does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws UnprocessableEntityException (HTTP 422) when the role is inactive or a code is unknown
   * @throws ProtectedRoleException when a role administration permission would be removed from the
   *     {@code ADMINISTRATOR} role
   */
  @Transactional
  public RolePermissionsResponse execute(
      UUID roleId, UpdateRolePermissionsCommand command, UserPrincipal principal) {
    requirePermission(principal);
    if (roleId == null || command == null)
      throw new IllegalArgumentException("Role ID and command are required");
    Set<String> grant = normalize(command.grant());
    Set<String> revoke = normalize(command.revoke());
    if (grant.isEmpty() && revoke.isEmpty())
      throw new IllegalArgumentException("At least one permission change is required");
    if (!Collections.disjoint(grant, revoke))
      throw new IllegalArgumentException("A permission cannot be granted and removed together");

    Role role =
        roles.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    if (!Objects.equals(command.rowVersion(), role.rowVersion()))
      throw new ConcurrentUpdateException();
    if (!Boolean.TRUE.equals(role.active()))
      throw new UnprocessableEntityException("The role is inactive");
    Set<String> requested = new HashSet<>(grant);
    requested.addAll(revoke);
    if (!Objects.equals(roles.countExistingPermissions(requested), Long.valueOf(requested.size())))
      throw new UnprocessableEntityException("A permission does not exist");
    role.requireRevocable(revoke);

    Set<String> current = new HashSet<>(roles.findGrantedPermissionCodes(role.id()));
    List<String> granted = grant.stream().filter(code -> !current.contains(code)).sorted().toList();
    List<String> removed = revoke.stream().filter(current::contains).sorted().toList();

    // The version moves even when every change is already in place, so a second save based on the
    // same read is reported as a conflict instead of silently succeeding.
    roles.incrementRowVersion(role.id(), command.rowVersion());
    roles.grantPermissions(role.id(), granted);
    roles.revokePermissions(role.id(), removed);
    Long newRowVersion = role.rowVersion() + 1;

    Map<String, Object> after = new HashMap<>();
    after.put("roleCode", role.code());
    after.put("granted", granted);
    after.put("removed", removed);
    after.put("rowVersion", newRowVersion);
    audit.record(
        principal.userId(),
        UPDATE_ROLE_PERMISSIONS,
        "ROLE",
        role.id(),
        Map.of("rowVersion", role.rowVersion()),
        after);
    log.info(
        "Role permissions update executed; commit pending: roleId={}, granted={}, removed={}",
        role.id(),
        granted.size(),
        removed.size());
    return RolePermissionsResponse.builder()
        .roleId(role.id())
        .roleCode(role.code())
        .active(role.active())
        .rowVersion(newRowVersion)
        .permissionCodes(roles.findGrantedPermissionCodes(role.id()))
        .build();
  }

  /** Trimmed, distinct, non-blank codes in request order. */
  private static Set<String> normalize(List<String> codes) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String code : new ArrayList<>(codes)) {
      if (code == null || code.isBlank())
        throw new IllegalArgumentException("Permission code is required");
      normalized.add(code.trim());
    }
    return normalized;
  }

  /**
   * Requires permission to change role permissions. An actor ID alone proves nothing: the principal
   * must be a staff account whose roles carry the permission (docs/api/roles-and-permissions.md).
   */
  private static void requirePermission(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(ASSIGN_PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
