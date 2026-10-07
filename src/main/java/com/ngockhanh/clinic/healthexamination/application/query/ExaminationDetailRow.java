package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read contract of one Participant row of the examination detail matrix and its Excel export. The
 * identification number is complete here and is masked wherever it leaves the application.
 *
 * @param performedBatchServiceIds batch services recorded as performed, in identifier order
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
    LocalDate examinationDate,
    String attendanceStatus,
    LocalDate actualExaminationDate,
    String reconciliationStatus,
    List<UUID> performedBatchServiceIds,
    long rowVersion) {
  public ExaminationDetailRow {
    performedBatchServiceIds = List.copyOf(performedBatchServiceIds);
  }
}
