package com.ngockhanh.clinic.accesscontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.accesscontrol.domain.valueobject.TemporaryPasswordPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TemporaryPasswordPolicyTest {
  @ParameterizedTest
  @ValueSource(strings = {"Abcdef1!", "Xk7#mP2q", "Mật khẩu-01", "ZZZZZZ9@"})
  void acceptsPasswordsWithLengthUppercaseDigitAndSpecialCharacter(String password) {
    assertThat(TemporaryPasswordPolicy.isSatisfiedBy(password)).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "Abcde1!", // 7 characters
        "abcdef1!", // no uppercase letter
        "Abcdefg!", // no digit
        "Abcdefg1", // no special character
        "Abcdef1 ", // a space is not a special character
      })
  void rejectsPasswordsMissingARule(String password) {
    assertThat(TemporaryPasswordPolicy.isSatisfiedBy(password)).isFalse();
  }
}
