package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.UUID;
import lombok.Builder;

/**
 * Input for moving every expected Participant of a session to another session, for example when the
 * company postpones the whole day. Attended Participants stay where they are.
 *
 * @param targetSessionId session the Participants go to
 */
@Builder
public record MoveSessionAssignmentsCommand(UUID targetSessionId) {}
