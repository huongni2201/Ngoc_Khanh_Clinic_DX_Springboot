package com.ngockhanh.clinic.healthexamination.application.service;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExaminationDateRulesTest {
  // 17:30 UTC on 2026-10-06 is 00:30 on 2026-10-07 in Asia/Ho_Chi_Minh.
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-06T17:30:00Z"), ZoneOffset.UTC);
  private final HealthExaminationBatch batch =
      draftBatch(UUID.randomUUID(), UUID.randomUUID(), 0, UUID.randomUUID());

  @Test
  void todayIsTheBusinessDayNotTheServerDay() {
    assertThat(ExaminationDateRules.today(CLOCK)).isEqualTo(LocalDate.of(2026, 10, 7));
  }

  @Test
  void anActualDateIsBetweenTheBatchStartAndToday() {
    assertThatCode(
            () -> {
              ExaminationDateRules.requireActualDateInRange(batch, null, CLOCK, "Row 3");
              ExaminationDateRules.requireActualDateInRange(batch, FIRST_DAY, CLOCK, "Row 3");
              ExaminationDateRules.requireActualDateInRange(
                  batch, LocalDate.of(2026, 10, 7), CLOCK, "Row 3");
            })
        .doesNotThrowAnyException();

    for (LocalDate bad : new LocalDate[] {FIRST_DAY.minusDays(1), LocalDate.of(2026, 10, 8)})
      assertThatThrownBy(() -> ExaminationDateRules.requireActualDateInRange(batch, bad, CLOCK, "Row 3"))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              e -> {
                assertThat(e.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT);
                assertThat(e.getMessage())
                    .isEqualTo(
                        "Row 3: actual_examination_date must be between the batch start date and today");
              });
  }

  @Test
  void aPlannedDateInTheFutureIsRefusedWithTheLocator() {
    assertThatCode(
            () ->
                ExaminationDateRules.requirePlannedDateNotInFuture(
                    LocalDate.of(2026, 10, 7), CLOCK, "Row 4"))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                ExaminationDateRules.requirePlannedDateNotInFuture(
                    LocalDate.of(2026, 10, 8), CLOCK, "Row 4"))
        .isInstanceOf(ApplicationException.class)
        .hasMessage(
            "Row 4: actual_examination_date is required because the planned examination date is in the future");
  }
}
