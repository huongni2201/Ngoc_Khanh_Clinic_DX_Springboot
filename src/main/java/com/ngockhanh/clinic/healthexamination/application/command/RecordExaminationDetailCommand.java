package com.ngockhanh.clinic.healthexamination.application.command;

import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Input of recording the examination details of one Participant by hand.
 *
 * <p>The command only carries the data. Whether the date and the services fit the attendance is a
 * business rule checked by the use case, so it holds for every caller, not only for the HTTP
 * request.
 *
 * @param attendance {@code ATTENDED} or {@code ABSENT}; the other attendance states cannot be
 *     recorded by hand and so cannot be expressed
 * @param actualExaminationDate actual date; required when attended, null when absent
 * @param performedBatchServiceIds batch services performed; at least one when attended, none when
 *     absent
 * @param rowVersion Participant row version the client last read
 */
public record RecordExaminationDetailCommand(
    Attendance attendance,
    LocalDate actualExaminationDate,
    Set<UUID> performedBatchServiceIds,
    long rowVersion) {
  /** Attendance outcomes that can be recorded by hand. */
  public enum Attendance {
    ATTENDED(AttendanceStatus.ATTENDED),
    ABSENT(AttendanceStatus.ABSENT);

    private final AttendanceStatus status;

    Attendance(AttendanceStatus status) {
      this.status = status;
    }

    /** The attendance status the Participant takes when this outcome is recorded. */
    public AttendanceStatus status() {
      return status;
    }
  }

  public RecordExaminationDetailCommand {
    performedBatchServiceIds =
        performedBatchServiceIds == null ? Set.of() : Set.copyOf(performedBatchServiceIds);
  }
}
