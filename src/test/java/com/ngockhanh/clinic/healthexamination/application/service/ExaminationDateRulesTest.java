package com.ngockhanh.clinic.healthexamination.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ExaminationDateRulesTest {
  // 17:30 UTC on 2026-10-06 is 00:30 on 2026-10-07 in Asia/Ho_Chi_Minh.
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-06T17:30:00Z"), ZoneOffset.UTC);

  @Test
  void todayIsTheBusinessDayNotTheServerDay() {
    assertThat(ExaminationDateRules.today(CLOCK)).isEqualTo(LocalDate.of(2026, 10, 7));
  }

  @Test
  void aSessionCanTakeAttendanceFromItsOwnDayOnwards() {
    assertThatCode(
            () -> {
              ExaminationDateRules.requireSessionStarted(LocalDate.of(2026, 10, 7), CLOCK);
              ExaminationDateRules.requireSessionStarted(LocalDate.of(2026, 10, 1), CLOCK);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void aSessionOfTomorrowCannotTakeAttendanceYet() {
    assertThatThrownBy(
            () -> ExaminationDateRules.requireSessionStarted(LocalDate.of(2026, 10, 8), CLOCK))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Attendance cannot be recorded before the session date");
  }
}
