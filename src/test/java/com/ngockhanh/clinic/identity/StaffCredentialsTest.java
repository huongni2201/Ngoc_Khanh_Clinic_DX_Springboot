package com.ngockhanh.clinic.identity;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.identity.infrastructure.security.UserPasswordEncoder;
import org.junit.jupiter.api.Test;

class StaffCredentialsTest {
  @Test
  void verifiesEncodedPasswordsAndNeverAcceptsPlaintext() {
    var encoder = new UserPasswordEncoder();
    String encoded = encoder.encode("correct-password");
    assertThat(encoded).startsWith("{bcrypt}$2");
    assertThat(encoder.matches("correct-password", encoded)).isTrue();
    assertThat(encoder.matches("wrong", encoded)).isFalse();
    assertThat(encoder.matches("correct-password", "correct-password")).isFalse();
    assertThat(encoder.matches("correct-password", null)).isFalse();
  }

  @Test
  void rejectsUtf8PasswordLongerThanBcryptLimit() {
    var encoder = new UserPasswordEncoder();
    assertThatThrownBy(() -> encoder.encode("é".repeat(37)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
