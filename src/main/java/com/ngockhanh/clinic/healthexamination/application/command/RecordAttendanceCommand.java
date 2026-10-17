package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for recording what happened to one assignment.
 *
 * @param status {@code ATTENDED}, {@code NO_SHOW} or {@code ASSIGNED} (back to expected)
 * @param rowVersion assignment version the caller last read; must match the stored version
 * @param note optional note
 */
@Builder
public record RecordAttendanceCommand(String status, Long rowVersion, String note) {}
