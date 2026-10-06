package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserPasswordEncoderTest {
  private final UserPasswordEncoder encoder = new UserPasswordEncoder();

  @Test
  void hashesWithBcryptPrefixAndVerifiesOnlyTheSamePassword() {
    String hash = encoder.encode("correct-password");
    assertThat(hash).startsWith("{bcrypt}$2");
    assertThat(encoder.matches("correct-password", hash)).isTrue();
    assertThat(encoder.matches("wrong", hash)).isFalse();
    assertThat(encoder.matches(" correct-password ", hash)).isFalse();
    assertThat(encoder.matches("correct-password", "correct-password")).isFalse();
    assertThat(encoder.matches("correct-password", null)).isFalse();
  }

  @Test
  void normalizesUnicodeSoEquivalentInputsMatch() {
    String composed = "Mật khẩu"; // precomposed Vietnamese letters
    String decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD);
    assertThat(encoder.matches(decomposed, encoder.encode(composed))).isTrue();
  }

  @Test
  void rejectsPasswordsLongerThan72Utf8Bytes() {
    String vietnamese = "ậA".repeat(19); // 19 x (3 + 1) bytes = 76 bytes, 38 characters
    assertThatThrownBy(() -> encoder.encode(vietnamese))
        .isInstanceOf(IllegalArgumentException.class);
    String hash = encoder.encode("short");
    assertThat(encoder.matches(vietnamese, hash)).isFalse();
  }
}
