package com.ngockhanh.clinic.identity.infrastructure.security;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public final class StaffPasswordEncoder implements com.ngockhanh.clinic.identity.application.port.Passwords {
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
  private final String dummyHash = bcrypt.encode("not-an-account-password");

  public String encode(String password) {
    validate(password);
    return "{bcrypt}" + bcrypt.encode(password);
  }

  public boolean matches(String password, String encoded) {
    validate(password);
    boolean supported = encoded != null && encoded.matches("\\{bcrypt}\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}");
    boolean matched = bcrypt.matches(password, supported ? encoded.substring(8) : dummyHash);
    return supported && matched;
  }

  public static void validate(String password) {
    if (password == null || password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException("Password must contain 1 to 72 UTF-8 bytes");
    }
  }
}
