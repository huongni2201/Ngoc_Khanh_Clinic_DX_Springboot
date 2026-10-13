package com.ngockhanh.clinic.accesscontrol.application.service;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import org.springframework.stereotype.Component;

/**
 * Authorizes the user account creation use cases (UC-005) for every supported caller, not only
 * HTTP. An actor ID alone proves nothing: the principal must be a staff account whose roles carry
 * the permission.
 */
@Component
public class UserAccountAccessPolicy {
  public static final String CREATE_PERMISSION = "USER_ACCOUNT_CREATE";
  public static final String OWNER_SEARCH_PERMISSION = "USER_ACCOUNT_OWNER_SEARCH";
  public static final String USERNAME_CHECK_PERMISSION = "USER_ACCOUNT_USERNAME_CHECK";
  public static final String ASSIGNABLE_ROLE_VIEW_PERMISSION = "USER_ACCOUNT_ASSIGNABLE_ROLE_VIEW";

  /**
   * Requires permission to create a user account.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireCreate(UserPrincipal principal) {
    require(principal, CREATE_PERMISSION);
  }

  /**
   * Requires permission to search account owner candidates.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireOwnerSearch(UserPrincipal principal) {
    require(principal, OWNER_SEARCH_PERMISSION);
  }

  /**
   * Requires permission to check whether a username is available.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireUsernameCheck(UserPrincipal principal) {
    require(principal, USERNAME_CHECK_PERMISSION);
  }

  /**
   * Requires permission to list the roles that can be granted to a new staff account.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireAssignableRoleView(UserPrincipal principal) {
    require(principal, ASSIGNABLE_ROLE_VIEW_PERMISSION);
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
