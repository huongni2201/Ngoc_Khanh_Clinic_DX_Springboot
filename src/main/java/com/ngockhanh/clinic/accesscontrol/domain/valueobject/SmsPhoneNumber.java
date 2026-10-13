package com.ngockhanh.clinic.accesscontrol.domain.valueobject;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A 10-digit Vietnamese mobile number that can receive sign-in information by SMS (UC-005). Stored
 * phone numbers are free text, so they are normalized before the check.
 *
 * @param value normalized number such as {@code 0912345678}
 */
public record SmsPhoneNumber(String value) {
  private static final Pattern MOBILE = Pattern.compile("^0[35789]\\d{8}$");
  private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");

  public SmsPhoneNumber {
    if (value == null || !MOBILE.matcher(value).matches())
      throw new IllegalArgumentException("Not a Vietnamese mobile number");
  }

  /**
   * Normalizes a stored phone number: removes spaces, dots, dashes and parentheses and turns a
   * leading {@code +84} or {@code 84} country code into {@code 0}.
   *
   * @param raw phone number as stored on a staff or patient profile, possibly null
   * @return the mobile number, or empty when the value is missing or not a mobile number
   */
  public static Optional<SmsPhoneNumber> parse(String raw) {
    if (raw == null) return Optional.empty();
    String digits = SEPARATORS.matcher(raw).replaceAll("");
    if (digits.startsWith("+84")) digits = "0" + digits.substring(3);
    else if (digits.startsWith("84") && digits.length() == 11) digits = "0" + digits.substring(2);
    return MOBILE.matcher(digits).matches()
        ? Optional.of(new SmsPhoneNumber(digits))
        : Optional.empty();
  }
}
