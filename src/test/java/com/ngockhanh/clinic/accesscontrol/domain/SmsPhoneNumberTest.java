package com.ngockhanh.clinic.accesscontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SmsPhoneNumber;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SmsPhoneNumberTest {
  @ParameterizedTest
  @CsvSource({
    "0912345678, 0912345678",
    "'0912 345 678', 0912345678",
    "0912.345.678, 0912345678",
    "(091) 234-5678, 0912345678",
    "+84912345678, 0912345678",
    "84 912 345 678, 0912345678",
    "0387654321, 0387654321",
    "0567654321, 0567654321",
    "0787654321, 0787654321",
    "0887654321, 0887654321"
  })
  void normalizesStoredMobileNumbers(String stored, String expected) {
    assertThat(SmsPhoneNumber.parse(stored)).contains(new SmsPhoneNumber(expected));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"02431234567", "0243123456", "091234567", "09123456789", "abc", "1900"})
  void rejectsLandlinesShortLongAndMissingNumbers(String stored) {
    assertThat(SmsPhoneNumber.parse(stored)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0212345678", "912345678"})
  void constructorAcceptsOnlyNormalizedMobileNumbers(String value) {
    assertThatThrownBy(() -> new SmsPhoneNumber(value))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
