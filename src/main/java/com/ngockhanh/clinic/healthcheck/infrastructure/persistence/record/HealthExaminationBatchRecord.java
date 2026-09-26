package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HealthExaminationBatchRecord(
        UUID id,
        UUID organizationId,
        String batchCode,
        String batchName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String payerType,
        String examinationSiteType,
        String examinationSiteName,
        String examinationSiteAddress,
        UUID masterTemplateVersionId,
        String status,
        OffsetDateTime finalizedAt,
        OffsetDateTime closedAt,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
