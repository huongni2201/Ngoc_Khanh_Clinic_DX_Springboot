package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.LocalDate;

/**
 * One data row read from the import workbook, typed but not yet checked against the domain.
 *
 * @param rowNumber real one-based row number in the worksheet (the first data row is 2)
 * @param participantCode optional text, null when blank
 * @param identificationNumber text exactly as typed, never trimmed or padded
 * @param phone optional text, null when blank
 * @param email optional text, null when blank
 */
public record ParticipantImportRow(
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
    LocalDate examinationDate) {}
