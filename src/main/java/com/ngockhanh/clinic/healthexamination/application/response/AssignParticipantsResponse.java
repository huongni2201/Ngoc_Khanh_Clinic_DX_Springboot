package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of scheduling Participants into a session: who was assigned and who was left out because
 * they were already in the session.
 */
public record AssignParticipantsResponse(
    UUID sessionId, List<Assigned> assigned, List<UUID> skippedParticipantIds) {
  public AssignParticipantsResponse {
    assigned = List.copyOf(assigned);
    skippedParticipantIds = List.copyOf(skippedParticipantIds);
  }

  /** One new assignment. */
  public record Assigned(UUID participantId, UUID assignmentId) {}
}
