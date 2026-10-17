package com.ngockhanh.clinic.healthexamination.domain.enums;

/** State of one Participant in one session. */
public enum AssignmentStatus {
  ASSIGNED,
  ATTENDED,
  NO_SHOW,
  MOVED,
  CANCELLED;

  /** Whether the assignment still counts for its session (the person is expected or came). */
  public boolean isLive() {
    return this == ASSIGNED || this == ATTENDED;
  }
}
