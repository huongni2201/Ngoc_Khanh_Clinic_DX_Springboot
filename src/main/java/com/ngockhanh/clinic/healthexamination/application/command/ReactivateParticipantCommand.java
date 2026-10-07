package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.UUID;

/**
 * Input of returning a cancelled Participant to the active roster.
 *
 * @param expectedRowVersion version the caller last read; required and not negative
 * @param batchDayId examination day to put the Participant on, or {@code null} to keep the day it
 *     had when it was cancelled
 */
public record ReactivateParticipantCommand(Long expectedRowVersion, UUID batchDayId) {}
