package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read contract of one Participant row of the examination detail matrix and its Excel export. The
 * identification number is complete here and is masked wherever it leaves the application.
 *
 * @param nextSessionDate date of the earliest session the Participant is still expected at, or null
 * @param attendanceStatus attendance derived from the session assignments
 * @param lastAttendedDate date of the latest session the Participant attended, or null
 * @param performedAgreementServiceIds agreement services recorded as performed, in identifier order
 */
public record ExaminationDetailRow(
    UUID id,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String departmentName,
    String positionName,
    LocalDate nextSessionDate,
    String attendanceStatus,
    LocalDate lastAttendedDate,
    String reconciliationStatus,
    List<UUID> performedAgreementServiceIds,
    long rowVersion) {
  public ExaminationDetailRow {
    performedAgreementServiceIds = List.copyOf(performedAgreementServiceIds);
  }
}
