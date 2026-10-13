package com.ngockhanh.clinic.healthexamination.application.contract;

import java.time.LocalDate;

/**
 * Plain facts of one Participant and its batch from which an examination form is printed. Text may
 * be {@code null} or blank when the roster does not have it.
 *
 * @param participantCode system code of the Participant, printed as the book number
 * @param sex {@code MALE}, {@code FEMALE}, {@code OTHER} or {@code UNKNOWN}
 * @param examinationDate planned date of the Participant's examination day
 * @param printedDate date the form is printed
 */
public record ExaminationFormSource(
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    LocalDate identificationIssueDate,
    String identificationIssuePlace,
    String ethnicity,
    String phone,
    String address,
    String workplace,
    String departmentName,
    String positionName,
    String organizationName,
    String organizationAddress,
    String batchCode,
    String batchName,
    LocalDate examinationDate,
    LocalDate printedDate) {}
