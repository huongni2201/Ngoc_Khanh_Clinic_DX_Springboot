package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record HealthExaminationImportJobRecord(
        UUID id,
        UUID healthExaminationBatchId,
        String importType,
        UUID sourceFileAttachmentId,
        String status,
        String columnMappingJson,
        Integer totalRows,
        Integer validRows,
        Integer warningRows,
        Integer errorRows,
        UUID createdByUserId,
        UUID confirmedByUserId,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt
) {
}
