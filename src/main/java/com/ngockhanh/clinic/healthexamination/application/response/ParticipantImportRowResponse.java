package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ParticipantImportRowResponse(
    int rowNumber,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String maskedIdentificationNumber,
    String departmentName,
    String positionName,
    UUID batchDayId,
    List<String> errors) {
  public static ParticipantImportRowResponse from(HealthExaminationImportRow row) {
    String number =
        row.getIdentificationNumber() == null ? null : row.getIdentificationNumber().value();
    String masked =
        number == null ? null : "••••••" + number.substring(Math.max(0, number.length() - 4));
    return new ParticipantImportRowResponse(
        row.getRowNumber(),
        row.getParticipantCode(),
        row.getFullName(),
        row.getDateOfBirth(),
        row.getSex(),
        masked,
        row.getDepartmentName(),
        row.getPositionName(),
        row.getBatchDayId() == null ? null : row.getBatchDayId().value(),
        row.getErrorCodes());
  }
}
