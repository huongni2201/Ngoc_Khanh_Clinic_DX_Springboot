package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Account password hashing: NFKC-normalized, bcrypt, stored with the {@code {bcrypt}} prefix.
 * Bcrypt only uses the first 72 bytes, so longer passwords are rejected instead of truncated.
 */
public final class UserPasswordEncoder implements PasswordEncoder {
  private static final String PREFIX = "{bcrypt}";
  private static final Integer MAX_BYTES = 72;

  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  @Override
  public String encode(CharSequence rawPassword) {
    String normalized = normalize(rawPassword);
    if (normalized == null || tooLong(normalized))
      throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
    return PREFIX + bcrypt.encode(normalized);
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    String normalized = normalize(rawPassword);
    if (normalized == null || encodedPassword == null || !encodedPassword.startsWith(PREFIX))
      return false;
    if (tooLong(normalized)) return false;
    return bcrypt.matches(normalized, encodedPassword.substring(PREFIX.length()));
  }

  private static String normalize(CharSequence rawPassword) {
    return rawPassword == null ? null : Normalizer.normalize(rawPassword, Normalizer.Form.NFKC);
  }

  private static Boolean tooLong(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
  }
}
