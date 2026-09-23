package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record HealthCheckBatchRecord(
        UUID id,
        UUID companyId,
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
        LocalDateTime finalizedAt,
        LocalDateTime closedAt,
        UUID createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
