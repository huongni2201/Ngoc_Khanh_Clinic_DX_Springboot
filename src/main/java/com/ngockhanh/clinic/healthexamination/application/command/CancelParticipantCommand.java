package com.ngockhanh.clinic.healthexamination.application.command;

/**
 * Input of cancelling one Participant.
 *
 * @param expectedRowVersion version the caller last read; required and not negative
 */
public record CancelParticipantCommand(Long expectedRowVersion) {}
