package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.UUID;
import lombok.Builder;

/**
 * Input for moving one Participant to another session.
 *
 * @param targetSessionId session the Participant goes to
 * @param rowVersion assignment version the caller last read; must match the stored version
 */
@Builder
public record MoveAssignmentCommand(UUID targetSessionId, Long rowVersion) {}
