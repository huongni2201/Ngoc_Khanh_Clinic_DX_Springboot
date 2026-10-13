package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AssignableRoleResponse;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the active staff roles an Administrator can grant to a new STAFF account (UC-005). The
 * PATIENT role is excluded because it is reserved for patient accounts.
 */
@Service
@RequiredArgsConstructor
public class ListAssignableRolesUseCase {
  /**
   * Permission required to list the roles that can be granted to a new staff account (ADR-0015,
   * V005).
   */
  public static final String PERMISSION = "USER_ACCOUNT_ASSIGNABLE_ROLE_VIEW";

  private final RoleRepository roles;

  /**
   * Lists the roles.
   *
   * @param principal authenticated Administrator
   * @return active non-patient roles ordered by name then code
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException of type {@code
   *     ACCESS_DENIED} without the assignable role view permission
   */
  @Transactional(readOnly = true)
  public List<AssignableRoleResponse> execute(UserPrincipal principal) {
    requirePermission(principal);
    return roles.findActiveStaffRoles().stream()
        .map(
            role ->
                new AssignableRoleResponse(role.id(), role.code(), role.name(), role.description()))
        .toList();
  }

  /**
   * Authorizes every supported caller, not only HTTP: an actor ID alone proves nothing, the
   * principal must be a STAFF account whose roles carry {@link #PERMISSION}.
   */
  private static void requirePermission(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
