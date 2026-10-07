package com.ngockhanh.clinic.healthexamination.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input of adding one Participant to a batch by hand. It carries no actor and no transport type.
 *
 * @param batchDayId examination day of the batch the Participant is scheduled on
 */
public record CreateParticipantCommand(
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String phone,
    String email,
    String departmentName,
    String positionName,
    UUID batchDayId) {}
