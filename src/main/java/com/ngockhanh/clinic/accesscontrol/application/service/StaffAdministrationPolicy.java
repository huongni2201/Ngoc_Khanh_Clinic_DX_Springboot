package com.ngockhanh.clinic.accesscontrol.application.service;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import org.springframework.stereotype.Component;

/**
 * Authorizes the staff administration use cases for every supported caller, not only HTTP. An
 * actor ID alone proves nothing: the principal must be a staff account whose roles carry the
 * permission (docs/api/staff-members.md).
 */
@Component
public class StaffAdministrationPolicy {
  public static final String VIEW_PERMISSION = "STAFF_MEMBER_VIEW";
  public static final String ROLE_VIEW_PERMISSION = "ROLE_VIEW";
  public static final String CREATE_PERMISSION = "USER_ACCOUNT_CREATE";
  public static final String ROLE_ASSIGN_PERMISSION = "USER_ROLE_ASSIGN";

  /**
   * Requires permission to list staff members.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireView(UserPrincipal principal) {
    require(principal, VIEW_PERMISSION);
  }

  /**
   * Requires permission to list the assignable roles.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireRoleView(UserPrincipal principal) {
    require(principal, ROLE_VIEW_PERMISSION);
  }

  /**
   * Requires permission to create an account and to grant its roles.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireCreate(UserPrincipal principal) {
    require(principal, CREATE_PERMISSION);
    require(principal, ROLE_ASSIGN_PERMISSION);
  }

  private static void require(UserPrincipal principal, String permission) {
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
