package com.ngockhanh.clinic.healthexamination.application.command;

import java.time.LocalDate;

/**
 * Input of editing one Participant. It replaces every roster field, except the system-generated
 * participant code, which never changes.
 *
 * @param expectedRowVersion version the caller last read; required and not negative
 */
public record UpdateParticipantCommand(
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
    Long expectedRowVersion) {}
