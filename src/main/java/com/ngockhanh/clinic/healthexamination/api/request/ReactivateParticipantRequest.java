package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.ReactivateParticipantCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

/**
 * Body for returning a cancelled Participant to the active roster. The Participant is scheduled
 * again explicitly; its cancelled assignments stay cancelled.
 *
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record ReactivateParticipantRequest(@NotNull @PositiveOrZero Long rowVersion) {

  /** Maps the transport body to the application command. */
  public ReactivateParticipantCommand toCommand() {
    return new ReactivateParticipantCommand(rowVersion);
  }
}
