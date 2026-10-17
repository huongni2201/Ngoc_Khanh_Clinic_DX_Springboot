package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of moving the expected Participants of a session to another session.
 *
 * @param movedCount Participants moved
 * @param skippedParticipantIds Participants left where they were because they were already in the
 *     target session
 */
public record MoveSessionAssignmentsResponse(
    UUID sourceSessionId, UUID targetSessionId, int movedCount, List<UUID> skippedParticipantIds) {
  public MoveSessionAssignmentsResponse {
    skippedParticipantIds = List.copyOf(skippedParticipantIds);
  }
}
