package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.MoveAssignmentCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for moving one Participant to another session.
 *
 * @param targetSessionId session the Participant goes to
 * @param rowVersion assignment version the client last read; required and not negative
 */
@Builder
public record MoveAssignmentRequest(
    @NotNull UUID targetSessionId, @NotNull @PositiveOrZero Long rowVersion) {

  /** Maps the transport body to the command. */
  public MoveAssignmentCommand toCommand() {
    return MoveAssignmentCommand.builder()
        .targetSessionId(targetSessionId)
        .rowVersion(rowVersion)
        .build();
  }
}
