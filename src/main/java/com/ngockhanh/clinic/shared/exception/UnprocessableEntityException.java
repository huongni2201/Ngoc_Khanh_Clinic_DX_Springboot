package com.ngockhanh.clinic.shared.exception;

/**
 * A well-formed request that refers to something the operation cannot use, for example an unknown
 * or inactive reference chosen by the caller. Mapped to HTTP 422 so callers can tell it apart from
 * a 409 conflict with existing data. The message is written for the caller and must not contain
 * personal data.
 */
public final class UnprocessableEntityException extends RuntimeException {
  public UnprocessableEntityException(String message) {
    super(message);
  }
}
