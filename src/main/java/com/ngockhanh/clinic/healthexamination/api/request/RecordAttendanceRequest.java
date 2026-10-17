package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.RecordAttendanceCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Body for recording what happened to one assignment.
 *
 * @param status {@code ATTENDED}, {@code NO_SHOW} or {@code ASSIGNED} (back to expected)
 * @param rowVersion assignment version the client last read; required and not negative
 * @param note optional note
 */
@Builder
public record RecordAttendanceRequest(
    @NotNull @Pattern(regexp = "ATTENDED|NO_SHOW|ASSIGNED") String status,
    @NotNull @PositiveOrZero Long rowVersion,
    @Size(max = 1000) String note) {

  /** Maps the transport body to the command. */
  public RecordAttendanceCommand toCommand() {
    return RecordAttendanceCommand.builder()
        .status(status)
        .rowVersion(rowVersion)
        .note(note)
        .build();
  }
}
