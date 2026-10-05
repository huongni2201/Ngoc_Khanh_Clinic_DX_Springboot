package com.ngockhanh.clinic.identity.application.response;

import lombok.Builder;

@Builder
public record LoginResult(String sessionId, UserSessionResponse response) {
  @Override
  public String toString() {
    return "LoginResult[accountId=" + response.accountId() + "]";
  }

  public static class LoginResultBuilder {
    @Override
    public String toString() {
      return "LoginResultBuilder[redacted]";
    }
  }
}
