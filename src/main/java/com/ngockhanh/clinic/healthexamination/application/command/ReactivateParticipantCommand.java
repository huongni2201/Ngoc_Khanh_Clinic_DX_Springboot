package com.ngockhanh.clinic.healthexamination.application.command;

/**
 * Input of returning a cancelled Participant to the active roster.
 *
 * @param expectedRowVersion version the caller last read; required and not negative
 */
public record ReactivateParticipantCommand(Long expectedRowVersion) {}
