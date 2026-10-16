package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/** A row in public.health_examination_agreements. */
@Builder
public record HealthExaminationAgreementRecord(
    UUID id,
    UUID organizationId,
    String agreementCode,
    String name,
    String status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion,
    Instant deletedAt,
    LocalDate validFrom,
    LocalDate validTo) {}
