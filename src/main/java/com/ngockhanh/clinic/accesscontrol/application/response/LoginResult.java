package com.ngockhanh.clinic.accesscontrol.application.response;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import lombok.Builder;

/**
 * Successful sign-in. The session ID belongs only in the session cookie; only {@code principal} is
 * returned in the response body.
 */
@Builder
public record LoginResult(String sessionId, UserPrincipal principal) {
  @Override
  public String toString() {
    return "LoginResult[userId=" + (principal == null ? null : principal.userId()) + "]";
  }
}
