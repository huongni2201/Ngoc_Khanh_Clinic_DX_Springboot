package com.ngockhanh.clinic.healthexamination.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input of adding one Participant to a batch by hand. It carries no actor and no transport type.
 *
 * <p>There is no participant code: the system generates it.
 *
 * @param batchDayId examination day of the batch the Participant is scheduled on
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
    String note,
    UUID batchDayId) {}
