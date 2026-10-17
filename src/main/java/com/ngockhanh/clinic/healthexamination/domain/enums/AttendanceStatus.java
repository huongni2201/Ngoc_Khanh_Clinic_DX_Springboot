package com.ngockhanh.clinic.healthexamination.domain.enums;

/**
 * Attendance of a Participant derived from the session assignments: {@code ATTENDED} when any
 * assignment was attended, {@code NO_SHOW} when none was attended but one was missed and none is
 * still expected, otherwise {@code UNCONFIRMED}. It is a read value, never stored.
 */
public enum AttendanceStatus {
  UNCONFIRMED,
  ATTENDED,
  NO_SHOW
}
