package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/** A row in public.health_examination_sessions. */
@Builder
public record HealthExaminationSessionRecord(
    UUID id,
    UUID organizationId,
    UUID agreementId,
    LocalDate sessionDate,
    String activity,
    String siteType,
    String siteAddress,
    String status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
