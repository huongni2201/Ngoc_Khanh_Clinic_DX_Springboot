package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.port.out.RoleDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.exception.ProtectedRoleException;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates the name, description and active flag of a role using optimistic locking (SRS UC-008
 * 2-AF-01 and 2-AF-02, 1.3.3 Edit Role). A deactivated role stays assigned to its accounts but
 * grants no permission from their next sign-in (BR-06). The update and its audit event run in one
 * transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateRoleUseCase {
  static final String UPDATE_ROLE = "UPDATE_ROLE";
  public static final String UPDATE_PERMISSION = "ROLE_UPDATE";

  private final RoleRepository roles;
  private final RoleDirectory directory;
  private final AuditWriter audit;

  /**
   * Updates the role when the caller's expected row version is still current.
   *
   * @param roleId role identifier
   * @param command new details and the expected row version
   * @param principal authenticated caller, recorded as the audit actor
   * @return the role as stored, with its usage counts and incremented row version
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code ROLE_UPDATE}
   * @throws IllegalArgumentException when a length or a required value is invalid
   * @throws ResourceNotFoundException when the role does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws ProtectedRoleException when the {@code ADMINISTRATOR} or {@code PATIENT} role would
   *     become inactive
   * @throws FieldConflictException (HTTP 409) when another role uses the name, ignoring case
   */
  @Transactional
  public RoleSummary execute(UUID roleId, UpdateRoleCommand command, UserPrincipal principal) {
    requirePermission(principal);
    if (roleId == null || command == null)
      throw new IllegalArgumentException("Role ID and command are required");
    Role role =
        roles.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    if (!Objects.equals(command.rowVersion(), role.rowVersion()))
      throw new ConcurrentUpdateException();

    Map<String, Object> before = snapshot(role);
    role.update(command.name(), command.description(), command.active());
    if (roles.existsByName(role.name(), role.id()))
      throw new FieldConflictException("name", "Role name already exists");
    roles.update(role, command.rowVersion());

    RoleSummary updated = directory.findSummary(role.id()).orElseThrow();
    Map<String, Object> after = snapshot(role);
    after.put("rowVersion", updated.rowVersion());
    audit.record(principal.userId(), UPDATE_ROLE, "ROLE", role.id(), before, after);
    log.info("Role update executed; commit pending: roleId={}", role.id());
    return updated;
  }

  private static Map<String, Object> snapshot(Role role) {
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("name", role.name());
    snapshot.put("description", role.description());
    snapshot.put("active", role.active());
    snapshot.put("rowVersion", role.rowVersion());
    return snapshot;
  }

  /**
   * Requires permission to update roles. An actor ID alone proves nothing: the principal must be a
   * staff account whose roles carry the permission (docs/api/roles-and-permissions.md).
   */
  private static void requirePermission(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(UPDATE_PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
