package com.ngockhanh.clinic.healthexamination.domain.enums;

public enum ParticipantImportField {
    FULL_NAME(true),
    SEX(true),
    DATE_OF_BIRTH(true),
    IDENTIFICATION_NUMBER(true),
    PHONE(false),
    IDENTIFICATION_NUMBER_ISSUE_DATE(false),
    IDENTIFICATION_NUMBER_ISSUE_PLACE(false),
    ETHNICITY(false),
    SUBJECT_TYPE(false),
    BLOOD_GROUP(false),
    OCCUPATION(false),
    WORKPLACE_OR_SCHOOL(false),
    ADDRESS_DETAIL(false),
    PAYER_SOURCE(false),
    ROSTER_NOTE(false);

    private final boolean required;

    ParticipantImportField(boolean required) {
        this.required = required;
    }

    public boolean required() {
        return required;
    }
}
