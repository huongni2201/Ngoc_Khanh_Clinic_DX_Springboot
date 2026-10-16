package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.port.out.RoleDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a role, optionally with the permissions of an existing active role (SRS UC-008 normal
 * flow, 1.3.2 New Role). The role, the copied grants and the audit event are written in one
 * transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateRoleUseCase {
  static final String CREATE_ROLE = "CREATE_ROLE";
  public static final String CREATE_PERMISSION = "ROLE_CREATE";
  public static final String ASSIGN_PERMISSION = UpdateRolePermissionsUseCase.ASSIGN_PERMISSION;

  private final RoleRepository roles;
  private final RoleDirectory directory;
  private final AuditWriter audit;

  /**
   * Creates the role.
   *
   * @param command role details and the optional source of permissions
   * @param principal authenticated caller, recorded as the audit actor
   * @return the created role with its usage counts
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code ROLE_CREATE}, or
   *     without {@code ROLE_PERMISSION_ASSIGN} when permissions are copied: a copy grants them
   * @throws IllegalArgumentException when the code format, a length or a required value is invalid
   * @throws FieldConflictException (HTTP 409) when the code or, ignoring case, the name is used
   * @throws UnprocessableEntityException (HTTP 422) when the source role is unknown, inactive or
   *     {@code PATIENT}
   */
  @Transactional
  public RoleSummary execute(CreateRoleCommand command, UserPrincipal principal) {
    requirePermission(principal, CREATE_PERMISSION);
    if (command == null) throw new IllegalArgumentException("Role command is required");
    if (command.copyFromRoleId() != null) requirePermission(principal, ASSIGN_PERMISSION);
    Role role =
        Role.create(
            UuidV7Generator.generate(),
            command.code(),
            command.name(),
            command.description(),
            command.active() == null ? Boolean.TRUE : command.active());
    if (roles.existsByCode(role.code()))
      throw new FieldConflictException("code", "Role code already exists");
    if (roles.existsByName(role.name(), null))
      throw new FieldConflictException("name", "Role name already exists");

    Role source =
        command.copyFromRoleId() == null
            ? null
            : roles
                .findById(command.copyFromRoleId())
                .filter(
                    candidate -> Boolean.TRUE.equals(candidate.active()) && !candidate.isPatient())
                .orElseThrow(
                    () -> new UnprocessableEntityException("The source role is not available"));

    roles.insert(role);
    if (source != null) roles.copyPermissions(source.id(), role.id());
    List<String> granted = source == null ? List.of() : roles.findGrantedPermissionCodes(role.id());

    Map<String, Object> after = new HashMap<>();
    after.put("code", role.code());
    after.put("name", role.name());
    after.put("description", role.description());
    after.put("active", role.active());
    if (source != null) after.put("copiedFromRoleCode", source.code());
    after.put("granted", granted);
    audit.record(principal.userId(), CREATE_ROLE, "ROLE", role.id(), null, after);
    log.info("Role insert executed; commit pending: roleId={}, code={}", role.id(), role.code());
    return directory.findSummary(role.id()).orElseThrow();
  }

  /**
   * Requires one permission. An actor ID alone proves nothing: the principal must be a staff
   * account whose roles carry the permission (docs/api/roles-and-permissions.md).
   */
  private static void requirePermission(UserPrincipal principal, String permission) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(permission));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
