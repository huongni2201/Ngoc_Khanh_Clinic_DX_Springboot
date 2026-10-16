package com.ngockhanh.clinic.shared.exception;

/**
 * A submitted value that conflicts with other stored data, reported as HTTP 409 with the rejected
 * field in {@code data} so a form can show the error under that field (owner-approved 2026-10-15,
 * first used by role administration). The code tells clients why the value is rejected, by default
 * {@link #DUPLICATE}. The message is English, written for developers and must not contain personal
 * data.
 */
public final class FieldConflictException extends RuntimeException {
  /** {@code code} of the field detail: another record already uses the value. */
  public static final String DUPLICATE = "Duplicate";

  private final String field;
  private final String code;

  /**
   * A value another record already uses.
   *
   * @param field JSON path of the request field, for example {@code code}
   * @param message English technical detail
   */
  public FieldConflictException(String field, String message) {
    this(field, DUPLICATE, message);
  }

  /**
   * @param field JSON path of the request field, for example {@code roleCode}
   * @param code stable reason clients can tell apart, for example {@code LastAdministrator}
   * @param message English technical detail
   */
  public FieldConflictException(String field, String code, String message) {
    super(message);
    if (field == null || field.isBlank()) throw new IllegalArgumentException("Missing field");
    if (code == null || code.isBlank()) throw new IllegalArgumentException("Missing code");
    this.field = field;
    this.code = code;
  }

  public String field() {
    return field;
  }

  public String code() {
    return code;
  }
}
