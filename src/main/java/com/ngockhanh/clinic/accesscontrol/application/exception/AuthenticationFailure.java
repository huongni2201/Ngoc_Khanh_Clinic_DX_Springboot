package com.ngockhanh.clinic.accesscontrol.application.exception;

import com.ngockhanh.clinic.shared.exception.ApplicationException;

/** Authentication failure with a safe, uniform client message. */
public final class AuthenticationFailure extends ApplicationException {
  private AuthenticationFailure(Type type, String message) {
    super(type, message);
  }

  /** Any rejected sign-in: unknown user, wrong password or ineligible account look identical. */
  public static AuthenticationFailure invalidCredentials() {
    return new AuthenticationFailure(Type.UNAUTHENTICATED, "Invalid username or password");
  }

  /** Missing, expired or unknown session. */
  public static AuthenticationFailure unauthenticated() {
    return new AuthenticationFailure(Type.UNAUTHENTICATED, "Authentication is required");
  }
}
