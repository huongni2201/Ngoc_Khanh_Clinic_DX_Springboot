package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.RolePermissionsResponse;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns the permissions granted to one role of any status (SRS UC-009, 1.3.4). */
@Service
@RequiredArgsConstructor
public class GetRolePermissionsUseCase {
  public static final String VIEW_PERMISSION = "ROLE_PERMISSION_VIEW";

  private final RoleRepository roles;

  /**
   * Returns the granted permission codes and the role version to send back when saving them.
   *
   * @param roleId role identifier
   * @param principal authenticated caller
   * @return the granted permissions, ordered by code
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code ROLE_PERMISSION_VIEW}
   * @throws ResourceNotFoundException when the role does not exist
   */
  @Transactional(readOnly = true)
  public RolePermissionsResponse execute(UUID roleId, UserPrincipal principal) {
    requirePermission(principal);
    if (roleId == null) throw new IllegalArgumentException("Role ID is required");
    Role role =
        roles.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    return RolePermissionsResponse.builder()
        .roleId(role.id())
        .roleCode(role.code())
        .active(role.active())
        .rowVersion(role.rowVersion())
        .permissionCodes(roles.findGrantedPermissionCodes(role.id()))
        .build();
  }

  /**
   * Requires permission to view role permissions. An actor ID alone proves nothing: the principal
   * must be a staff account whose roles carry the permission (docs/api/roles-and-permissions.md).
   */
  private static void requirePermission(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(VIEW_PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
