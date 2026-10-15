package com.ngockhanh.clinic.accesscontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.domain.valueobject.UsernameBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UsernameBaseTest {
  @ParameterizedTest
  @CsvSource({
    "Nguyễn Văn An, annv",
    "Đặng Thị Đào, daodt",
    "'  Trần   Bình  ', binht",
    "An, an",
    "NGUYỄN VĂN AN, annv",
    "Lê-Văn O'Neil, oneill",
    "Phạm 2 Hùng, hungp"
  })
  void buildsTheLastWordFollowedByTheInitialsOfEarlierWords(String fullName, String base) {
    assertThat(UsernameBase.from(fullName).value()).isEqualTo(base);
  }

  @Test
  void rejectsANameWithoutUsableLetters() {
    assertThatThrownBy(() -> UsernameBase.from("123 !!"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UsernameBase.from("   ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UsernameBase.from(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void truncatesALongBaseToLeaveRoomForTheNumber() {
    String base = UsernameBase.from("a".repeat(200)).value();
    assertThat(base).hasSize(UsernameBase.MAX_LENGTH);
  }

  @Test
  void acceptsOnlyLowercaseAsciiLetters() {
    assertThatThrownBy(() -> new UsernameBase("annv1")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new UsernameBase("ÂN")).isInstanceOf(IllegalArgumentException.class);
  }
}
