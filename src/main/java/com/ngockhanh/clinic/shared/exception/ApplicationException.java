package com.ngockhanh.clinic.shared.exception;

public class ApplicationException extends RuntimeException {
  public enum Type {
    INVALID_INPUT,
    UNAUTHENTICATED,
    ACCESS_DENIED,
    RATE_LIMITED,
    DEPENDENCY_UNAVAILABLE
  }

  private final Type type;
  private final long retryAfterSeconds;

  public ApplicationException(Type type, String message) {
    this(type, message, 0, null);
  }

  public ApplicationException(Type type, String message, long retryAfterSeconds) {
    this(type, message, retryAfterSeconds, null);
  }

  public ApplicationException(Type type, String message, Throwable cause) {
    this(type, message, 0, cause);
  }

  private ApplicationException(Type type, String message, long retryAfterSeconds, Throwable cause) {
    super(message, cause);
    this.type = type;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public Type type() {
    return type;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
