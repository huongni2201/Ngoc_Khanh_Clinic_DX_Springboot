package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.MoveSessionAssignmentsCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for moving every expected Participant of a session to another session.
 *
 * @param targetSessionId session the Participants go to
 */
@Builder
public record MoveSessionAssignmentsRequest(@NotNull UUID targetSessionId) {

  /** Maps the transport body to the command. */
  public MoveSessionAssignmentsCommand toCommand() {
    return MoveSessionAssignmentsCommand.builder().targetSessionId(targetSessionId).build();
  }
}
