package com.ngockhanh.clinic.healthexamination.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input of editing one Participant. It replaces every roster field and the examination day.
 *
 * @param expectedRowVersion version the caller last read; required and not negative
 */
public record UpdateParticipantCommand(
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String phone,
    String email,
    String departmentName,
    String positionName,
    UUID batchDayId,
    Long expectedRowVersion) {}
