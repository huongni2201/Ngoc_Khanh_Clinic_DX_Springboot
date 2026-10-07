package com.ngockhanh.clinic.shared.exception;

/** An uploaded file whose declared type is not supported by the endpoint (HTTP 415). */
public final class UnsupportedFileTypeException extends RuntimeException {
  public UnsupportedFileTypeException(String message) {
    super(message);
  }
}
