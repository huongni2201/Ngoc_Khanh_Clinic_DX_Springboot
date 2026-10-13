package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.UsernameAvailabilityResponse;
import com.ngockhanh.clinic.accesscontrol.application.service.UserAccountAccessPolicy;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
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
  private final UserAccountAccessPolicy access;
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
    access.requireUsernameCheck(principal);
    if (username == null || username.isBlank())
      throw new IllegalArgumentException("Username is required");
    return new UsernameAvailabilityResponse(!accounts.existsByUsername(username));
  }
}
