package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Date rules of recording attendance. "Today" is the business day in Vietnam, not the server day.
 */
public final class ExaminationDateRules {
  /** Business time zone used to decide what "today" is for a session date. */
  public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private ExaminationDateRules() {}

  /** Today in business time. */
  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(BUSINESS_ZONE));
  }

  /**
   * A session cannot have an attendance before its day (business time).
   *
   * @throws ConflictException when the session date is in the future
   */
  public static void requireSessionStarted(LocalDate sessionDate, Clock clock) {
    if (sessionDate.isAfter(today(clock)))
      throw new ConflictException("Attendance cannot be recorded before the session date");
  }
}
