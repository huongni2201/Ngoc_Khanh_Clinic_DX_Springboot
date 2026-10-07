package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/** A row in public.health_examination_record_snapshots. */
@Builder
public record HealthExaminationRecordSnapshotRecord(
    UUID id,
    UUID healthExaminationRecordId,
    int versionNo,
    String status,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String phone,
    String address,
    String organizationName,
    String participantCode,
    String departmentName,
    String positionName,
    Instant issuedAt,
    UUID issuedBy,
    long rowVersion,
    UUID createdBy,
    Instant createdAt) {}
