package com.ngockhanh.clinic.healthexamination.application.service;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * What one Participant is asked to become, independent of where the request came from: a row of an
 * Excel file or a single manual entry on screen.
 *
 * @param participantId Participant the entry is for
 * @param rowVersion Participant row version the caller saw
 * @param performedServiceIds batch services marked as performed; services left out are not performed
 * @param actualExaminationDate actual examination date, or null to keep the recorded or planned date
 * @param locator prefix of error messages, such as {@code Row 7} or {@code Participant}; it never
 *     carries personal data
 */
public record ExaminationDetailEntry(
    UUID participantId,
    long rowVersion,
    Set<UUID> performedServiceIds,
    LocalDate actualExaminationDate,
    String locator) {
  public ExaminationDetailEntry {
    performedServiceIds = Set.copyOf(performedServiceIds);
  }
}
