package com.ngockhanh.clinic.shared.exception;

/**
 * A submitted value that another record already uses, reported as HTTP 409 with the rejected field
 * in {@code data} so a form can show the error under that field (owner-approved 2026-10-15, first
 * used by role administration). The message is English, written for developers and must not contain
 * personal data.
 */
public final class FieldConflictException extends RuntimeException {
  /** {@code code} of the field detail. */
  public static final String DUPLICATE = "Duplicate";

  private final String field;

  /**
   * @param field JSON path of the request field, for example {@code code}
   * @param message English technical detail
   */
  public FieldConflictException(String field, String message) {
    super(message);
    if (field == null || field.isBlank()) throw new IllegalArgumentException("Missing field");
    this.field = field;
  }

  public String field() {
    return field;
  }
}
