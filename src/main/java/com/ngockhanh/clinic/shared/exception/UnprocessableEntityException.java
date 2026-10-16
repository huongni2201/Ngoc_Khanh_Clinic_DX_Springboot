package com.ngockhanh.clinic.shared.exception;

/**
 * A well-formed request that refers to something the operation cannot use, for example an unknown
 * or inactive reference chosen by the caller. Mapped to HTTP 422 so callers can tell it apart from
 * a 409 conflict with existing data. When several references can be rejected, the request field and
 * a stable code tell them apart in {@code data}. The message is written for the caller and must not
 * contain personal data.
 */
public final class UnprocessableEntityException extends RuntimeException {
  private final String field;
  private final String code;

  public UnprocessableEntityException(String message) {
    super(message);
    this.field = null;
    this.code = null;
  }

  /**
   * @param field JSON path of the rejected reference, for example {@code roleCode}
   * @param code stable reason clients can tell apart, for example {@code Unavailable}
   * @param message English technical detail
   */
  public UnprocessableEntityException(String field, String code, String message) {
    super(message);
    if (field == null || field.isBlank()) throw new IllegalArgumentException("Missing field");
    if (code == null || code.isBlank()) throw new IllegalArgumentException("Missing code");
    this.field = field;
    this.code = code;
  }

  /** The rejected request field, or null when the reference is not named. */
  public String field() {
    return field;
  }

  /** The reason code, or null when the reference is not named. */
  public String code() {
    return code;
  }
}
