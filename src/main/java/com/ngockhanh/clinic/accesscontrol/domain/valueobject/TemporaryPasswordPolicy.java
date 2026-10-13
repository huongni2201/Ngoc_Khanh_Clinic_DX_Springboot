package com.ngockhanh.clinic.accesscontrol.domain.valueobject;

/**
 * Password policy of the SRS (UC-003, UC-004, UC-005): at least 8 characters with at least one
 * uppercase letter, one digit and one special character. The 72-byte bcrypt limit is enforced by
 * the password encoder after Unicode normalization.
 */
public final class TemporaryPasswordPolicy {
  /** Minimum number of characters. */
  public static final int MIN_LENGTH = 8;

  private TemporaryPasswordPolicy() {}

  /**
   * Tells whether a password satisfies the policy. The value is never logged or kept.
   *
   * @param password candidate password, possibly null
   * @return true when every rule is met
   */
  public static boolean isSatisfiedBy(String password) {
    if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH)
      return false;
    boolean upper = false;
    boolean digit = false;
    boolean special = false;
    for (int i = 0; i < password.length(); ) {
      int codePoint = password.codePointAt(i);
      if (Character.isUpperCase(codePoint)) upper = true;
      else if (Character.isDigit(codePoint)) digit = true;
      else if (!Character.isLetter(codePoint) && !Character.isWhitespace(codePoint)) special = true;
      i += Character.charCount(codePoint);
    }
    return upper && digit && special;
  }
}
