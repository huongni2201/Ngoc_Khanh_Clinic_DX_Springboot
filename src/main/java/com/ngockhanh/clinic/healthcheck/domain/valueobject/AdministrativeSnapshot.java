package com.ngockhanh.clinic.healthcheck.domain.valueobject;

import java.time.LocalDate;

public record AdministrativeSnapshot(
        String fullName, LocalDate dateOfBirth, String sex, Cccd cccd,
        LocalDate cccdIssueDate, String cccdIssuePlace, String ethnicity, String subjectType,
        String payerSource, String bloodGroup, String phone, String province, String ward,
        String addressDetail, String occupation, String workplaceOrSchool, String healthCheckReason) {
    public AdministrativeSnapshot(String fullName, LocalDate dateOfBirth, String sex, Cccd cccd) {
        this(fullName, dateOfBirth, sex, cccd, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    public AdministrativeSnapshot {
        if (fullName == null || fullName.isBlank() || dateOfBirth == null || sex == null || sex.isBlank() || cccd == null) {
            throw new IllegalArgumentException("Missing required health-check identity");
        }
    }
}
