package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record HealthExaminationBatchRecord(
    UUID id,
    UUID organizationId,
    String batchCode,
    String name,
    String examinationSiteType,
    String examinationSiteName,
    String examinationSiteAddress,
    String status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion,
    Instant deletedAt) {}
