package com.ngockhanh.clinic.healthexamination.domain.enums;

public enum ParticipantImportField {
  FULL_NAME(true),
  SEX(true),
  DATE_OF_BIRTH(true),
  IDENTIFICATION_NUMBER(true),
  DEPARTMENT_NAME(true),
  POSITION_NAME(true),
  PARTICIPANT_CODE(false),
  PHONE(false),
  EMAIL(false);
  private final boolean required;

  ParticipantImportField(boolean required) {
    this.required = required;
  }

  public boolean required() {
    return required;
  }
}
