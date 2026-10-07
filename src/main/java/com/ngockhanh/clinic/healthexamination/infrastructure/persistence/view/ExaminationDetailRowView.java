package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.LocalDate;
import java.util.UUID;

/** SQL projection of one Participant row of the examination detail matrix and its export. */
public record ExaminationDetailRowView(
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
    long rowVersion) {}
