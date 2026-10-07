package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.ReactivateParticipantCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for returning a cancelled Participant to the active roster.
 *
 * @param rowVersion version the client last read; required and not negative
 * @param batchDayId examination day to put the Participant on; {@code null} keeps the day it had
 */
@Builder
public record ReactivateParticipantRequest(
    @NotNull @PositiveOrZero Long rowVersion, UUID batchDayId) {

  /** Maps the transport body to the application command. */
  public ReactivateParticipantCommand toCommand() {
    return new ReactivateParticipantCommand(rowVersion, batchDayId);
  }
}
