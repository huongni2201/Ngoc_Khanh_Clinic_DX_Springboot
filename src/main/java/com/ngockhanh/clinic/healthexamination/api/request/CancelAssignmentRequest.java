package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.CancelAssignmentCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Body for cancelling the assignment of a Participant.
 *
 * @param rowVersion assignment version the client last read; required and not negative
 * @param note optional reason
 */
@Builder
public record CancelAssignmentRequest(
    @NotNull @PositiveOrZero Long rowVersion, @Size(max = 1000) String note) {

  /** Maps the transport body to the command. */
  public CancelAssignmentCommand toCommand() {
    return CancelAssignmentCommand.builder().rowVersion(rowVersion).note(note).build();
  }
}
