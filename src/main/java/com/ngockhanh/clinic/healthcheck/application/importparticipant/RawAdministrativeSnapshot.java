package com.ngockhanh.clinic.healthcheck.application.importparticipant;

public record RawAdministrativeSnapshot(
        String fullName,
        String dateOfBirth,
        String sex,
        String identificationNumber,
        String identificationNumberIssueDate,
        String identificationNumberIssuePlace,
        String ethnicity,
        String subjectType,
        String payerSource,
        String bloodGroup,
        String phone,
        String province,
        String ward,
        String addressDetail,
        String occupation,
        String workplaceOrSchool,
        String healthExaminationReason) {
}
