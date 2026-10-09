package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.LocalDate;

/**
 * One data row read from the import workbook, typed but not yet checked against the domain.
 *
 * @param rowNumber real one-based row number in the worksheet (the first data row is 2)
 * <p>There is no participant code: the system generates it when the row is stored.
 *
 * @param identificationNumber text exactly as typed, never trimmed or padded
 * @param identificationIssueDate optional date, null when blank
 * @param identificationIssuePlace optional text, null when blank
 * @param ethnicity optional text, null when blank
 * @param phone optional text, null when blank
 * @param email optional text, null when blank
 * @param address optional text, null when blank
 * @param workplace optional text, null when blank
 * @param note optional text, null when blank
 */
public record ParticipantImportRow(
    int rowNumber,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    LocalDate identificationIssueDate,
    String identificationIssuePlace,
    String ethnicity,
    String phone,
    String email,
    String address,
    String workplace,
    String departmentName,
    String positionName,
    String note,
    LocalDate examinationDate) {}
