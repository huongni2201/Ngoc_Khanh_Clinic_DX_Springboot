package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Input for scheduling Participants into a session.
 *
 * @param participantIds Participants to schedule, without duplicates
 */
@Builder
public record AssignParticipantsCommand(List<UUID> participantIds) {
  public AssignParticipantsCommand {
    participantIds =
        participantIds == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(participantIds));
  }
}
