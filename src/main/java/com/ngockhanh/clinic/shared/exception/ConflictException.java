package com.ngockhanh.clinic.shared.exception;

/**
 * A request that conflicts with the current state of a resource or with other data in the same
 * request. The message is written for the caller and must not contain personal data.
 */
public final class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
