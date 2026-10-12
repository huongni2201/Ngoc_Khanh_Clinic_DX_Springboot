package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Date rules shared by every way of recording examination details (Excel import and manual entry).
 * "Today" is the business day in Vietnam, not the server day.
 */
public final class ExaminationDateRules {
  /** Business time zone used to decide what "today" is for an actual examination date. */
  public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private ExaminationDateRules() {}

  /** Today in business time. */
  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(BUSINESS_ZONE));
  }

  /**
   * An actual date is at least the first day of the batch and at most today (business time). A null
   * date is accepted: the caller decides whether the date is required.
   *
   * @param locator prefix of the error message, such as {@code Row 7}
   * @throws ApplicationException of type {@code INVALID_INPUT} when the date is out of range
   */
  public static void requireActualDateInRange(
      HealthExaminationBatch batch, LocalDate date, Clock clock, String locator) {
    if (date != null && (date.isBefore(batch.startDate()) || date.isAfter(today(clock))))
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          locator + ": actual_examination_date must be between the batch start date and today");
  }

  /**
   * A Participant who is attended on the planned date because no actual date was given must not
   * have a planned date in the future (business time).
   *
   * @param plannedDate planned examination date, or null when unknown
   * @param locator prefix of the error message, such as {@code Row 7}
   * @throws ApplicationException of type {@code INVALID_INPUT} when the planned date is in the future
   */
  public static void requirePlannedDateNotInFuture(
      LocalDate plannedDate, Clock clock, String locator) {
    if (plannedDate != null && plannedDate.isAfter(today(clock)))
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          locator
              + ": actual_examination_date is required because the planned examination date"
              + " is in the future");
  }
}
