package com.ngockhanh.clinic.healthexamination.domain.entity;

import com.ngockhanh.clinic.healthexamination.domain.enums.ImportRowAction;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public final class HealthExaminationImportRow {
  private final AggregateId id;
  private final int rowNumber;
  private final String participantCode;
  private final String fullName;
  private final LocalDate dateOfBirth;
  private final String sex;
  private final IdentificationNumber identificationNumber;
  private final String phone;
  private final String email;
  private final String departmentName;
  private final String positionName;
  private final List<String> errorCodes = new ArrayList<>();
  private AggregateId batchDayId;
  private AggregateId resolvedBatchParticipantId;

  public HealthExaminationImportRow(
      AggregateId id,
      int rowNumber,
      String participantCode,
      String fullName,
      LocalDate dateOfBirth,
      String sex,
      IdentificationNumber identificationNumber,
      String phone,
      String email,
      String departmentName,
      String positionName,
      List<String> errors) {
    if (id == null || rowNumber < 1) throw new IllegalArgumentException("Invalid import row");
    this.id = id;
    this.rowNumber = rowNumber;
    this.participantCode = participantCode;
    this.fullName = fullName;
    this.dateOfBirth = dateOfBirth;
    this.sex = sex;
    this.identificationNumber = identificationNumber;
    this.phone = phone;
    this.email = email;
    this.departmentName = departmentName;
    this.positionName = positionName;
    this.errorCodes.addAll(errors);
  }

  public boolean isValid() {
    return errorCodes.isEmpty();
  }

  public List<String> getErrorCodes() {
    return List.copyOf(errorCodes);
  }

  public void reject(String error) {
    if (!errorCodes.contains(error)) errorCodes.add(error);
  }

  public void assignDay(AggregateId dayId) {
    if (dayId == null || !isValid())
      throw new IllegalArgumentException("A valid row and examination day are required");
    this.batchDayId = dayId;
  }

  public void resolve(AggregateId participantId) {
    if (participantId == null || !isValid() || batchDayId == null)
      throw new IllegalArgumentException("Invalid committed import row");
    resolvedBatchParticipantId = participantId;
  }

  public ImportRowAction getAppliedAction() {
    return isValid() ? ImportRowAction.CREATE : null;
  }

  public List<String> getWarningCodes() {
    return List.of();
  }
}
