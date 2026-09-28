package com.ngockhanh.clinic.healthexamination.application.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrganizationBatchParticipantResponse(
        UUID batchParticipantId,
        UUID participantId,
        String participantCode,
        String departmentName,
        String jobTitle,
        String occupation,
        String fullName,
        LocalDate dateOfBirth,
        String sex,
        String identificationNumber,
        LocalDate identificationNumberIssueDate,
        String identificationNumberIssuePlace,
        String ethnicity,
        String subjectType,
        String payerSource,
        String bloodGroup,
        String phone,
        String province,
        String ward,
        String addressDetail,
        String administrativeOccupation,
        String workplaceOrSchool,
        String healthExaminationReason,
        String status,
        Instant createdAt) {
}
