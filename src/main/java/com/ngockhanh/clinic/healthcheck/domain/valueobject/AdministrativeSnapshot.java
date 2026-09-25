package com.ngockhanh.clinic.healthcheck.domain.valueobject;

import java.time.LocalDate;

public record AdministrativeSnapshot(
        String fullName, LocalDate dateOfBirth, String sex, IdentificationNumber identificationNumber,
        LocalDate identificationNumberIssueDate, String identificationNumberIssuePlace, String ethnicity, String subjectType,
        String payerSource, String bloodGroup, String phone, String province, String ward,
        String addressDetail, String occupation, String workplaceOrSchool, String healthExaminationReason) {
    public AdministrativeSnapshot(String fullName, LocalDate dateOfBirth, String sex, IdentificationNumber identificationNumber) {
        this(fullName, dateOfBirth, sex, identificationNumber, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    public AdministrativeSnapshot {
        if (fullName == null || fullName.isBlank() || dateOfBirth == null || sex == null || sex.isBlank() || identificationNumber == null) {
            throw new IllegalArgumentException("Missing required health-examination identity");
        }
    }
}
