package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/** A row in public.health_examination_participants. */
@Builder
public record HealthExaminationParticipantRecord(
    UUID id,
    UUID agreementId,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    LocalDate identificationIssueDate,
    String identificationIssuePlace,
    String ethnicity,
    String phone,
    String email,
    String address,
    String workplace,
    String departmentName,
    String positionName,
    String note,
    UUID patientId,
    String rosterStatus,
    String serviceReconciliationStatus,
    UUID servicesReconciledBy,
    Instant servicesReconciledAt,
    UUID importJobId,
    Integer sourceRowNumber,
    Instant preparedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion,
    UUID organizationId) {}
