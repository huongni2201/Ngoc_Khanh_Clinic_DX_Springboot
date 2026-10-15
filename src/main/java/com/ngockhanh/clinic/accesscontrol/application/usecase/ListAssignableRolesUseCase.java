package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.port.out.StaffMemberDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.RoleResponse;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the active roles, other than {@code PATIENT}, that may be granted to a staff account. */
@Service
@RequiredArgsConstructor
public class ListAssignableRolesUseCase {
  public static final String ROLE_VIEW_PERMISSION = "ROLE_VIEW";

  private final StaffMemberDirectory directory;

  /**
   * Returns the assignable roles ordered by code.
   *
   * @param principal authenticated caller
   * @return the roles
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code ROLE_VIEW}
   */
  @Transactional(readOnly = true)
  public List<RoleResponse> execute(UserPrincipal principal) {
    requireRoleView(principal);
    return directory.findAssignableRoles().stream().map(RoleResponse::from).toList();
  }

  /**
   * Requires permission to list the assignable roles. An actor ID alone proves nothing: the
   * principal must be a staff account whose roles carry the permission, whatever the caller
   * (docs/api/staff-members.md).
   */
  private static void requireRoleView(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(ROLE_VIEW_PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
