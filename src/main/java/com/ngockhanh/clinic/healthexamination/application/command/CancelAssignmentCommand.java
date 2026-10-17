package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for cancelling the assignment of a Participant.
 *
 * @param rowVersion assignment version the caller last read; must match the stored version
 * @param note optional reason
 */
@Builder
public record CancelAssignmentCommand(Long rowVersion, String note) {}
