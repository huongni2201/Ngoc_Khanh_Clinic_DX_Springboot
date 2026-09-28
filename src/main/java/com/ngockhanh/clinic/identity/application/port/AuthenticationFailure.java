package com.ngockhanh.clinic.identity.application.port;

public final class AuthenticationFailure extends RuntimeException {
  private final int status;
  private final long retryAfter;

  public AuthenticationFailure(int status, String message) {
    this(status, message, 0);
  }

  public AuthenticationFailure(int status, String message, long retryAfter) {
    super(message);
    this.status = status;
    this.retryAfter = retryAfter;
  }

  public int status() {
    return status;
  }

  public long retryAfter() {
    return retryAfter;
  }

  public static AuthenticationFailure invalid() {
    return new AuthenticationFailure(401, "Invalid credentials or session");
  }

  public static AuthenticationFailure unavailable() {
    return new AuthenticationFailure(503, "Authentication service unavailable");
  }
}
