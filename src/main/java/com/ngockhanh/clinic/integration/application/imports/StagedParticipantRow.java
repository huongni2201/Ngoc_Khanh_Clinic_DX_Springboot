package com.ngockhanh.clinic.integration.application.imports;

import java.time.LocalDate;

/** One validated row staged with its job. It owns the shape of the stored payload. */
public record StagedParticipantRow(
    int rowNumber,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String phone,
    String email,
    String departmentName,
    String positionName,
    LocalDate examinationDate) {
  public StagedParticipantRow {
    if (rowNumber < 1) throw new IllegalArgumentException("Row number must be positive");
  }
}
