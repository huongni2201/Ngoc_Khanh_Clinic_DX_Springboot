package com.ngockhanh.clinic.healthexamination.application.command;

import java.time.LocalDate;

/**
 * Input of adding one Participant to an agreement by hand. It carries no actor and no transport
 * type.
 *
 * <p>There is no participant code: the system generates it.
 */
public record CreateParticipantCommand(
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
    String note) {}
