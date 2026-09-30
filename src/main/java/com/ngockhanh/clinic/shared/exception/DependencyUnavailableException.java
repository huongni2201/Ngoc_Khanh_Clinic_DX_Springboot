package com.ngockhanh.clinic.shared.exception;

public final class DependencyUnavailableException extends RuntimeException {
  public DependencyUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
