package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.AssignParticipantsCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for scheduling Participants into a session.
 *
 * @param participantIds Participants to schedule, without duplicates
 */
@Builder
public record AssignParticipantsRequest(
    @NotEmpty @Size(max = 1000) List<@NotNull UUID> participantIds) {

  /** Maps the transport body to the command. */
  public AssignParticipantsCommand toCommand() {
    return AssignParticipantsCommand.builder().participantIds(participantIds).build();
  }
}
