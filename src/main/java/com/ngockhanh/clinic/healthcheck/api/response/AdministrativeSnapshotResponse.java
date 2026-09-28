package com.ngockhanh.clinic.healthcheck.api.response;

import java.time.LocalDate;

/** HTTP representation; preserves the existing administrative snapshot JSON shape. */
public record AdministrativeSnapshotResponse(
        String fullName, LocalDate dateOfBirth, String sex, IdentificationNumberResponse identificationNumber,
        LocalDate identificationNumberIssueDate, String identificationNumberIssuePlace, String ethnicity,
        String subjectType, String payerSource, String bloodGroup, String phone, String province, String ward,
        String addressDetail, String occupation, String workplaceOrSchool, String healthExaminationReason) {

    public record IdentificationNumberResponse(String value) {}
}
