package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.CancelParticipantCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

/**
 * Query parameters for cancelling a Participant.
 *
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record CancelParticipantRequest(@NotNull @PositiveOrZero Long rowVersion) {

  /** Maps the query parameters to the application command. */
  public CancelParticipantCommand toCommand() {
    return new CancelParticipantCommand(rowVersion);
  }
}
