package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.UsernameAvailabilityResponse;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells an Administrator whether a username is still free for a new account (UC-005). The answer is
 * advisory: account creation checks the username again.
 */
@Service
@RequiredArgsConstructor
public class CheckUsernameAvailabilityUseCase {
  /** Permission required to check whether a username is available (ADR-0015, V005). */
  public static final String PERMISSION = "USER_ACCOUNT_USERNAME_CHECK";

  private final UserAccountRepository accounts;

  /**
   * Checks the username, compared exactly as typed.
   *
   * @param username sign-in identifier
   * @param principal authenticated Administrator
   * @return whether no account uses the username
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException of type {@code
   *     ACCESS_DENIED} without the username check permission
   * @throws IllegalArgumentException if the username is missing
   */
  @Transactional(readOnly = true)
  public UsernameAvailabilityResponse execute(String username, UserPrincipal principal) {
    requirePermission(principal);
    if (username == null || username.isBlank())
      throw new IllegalArgumentException("Username is required");
    return new UsernameAvailabilityResponse(!accounts.existsByUsername(username));
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
