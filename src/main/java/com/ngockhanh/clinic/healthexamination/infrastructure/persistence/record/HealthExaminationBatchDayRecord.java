package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record HealthExaminationBatchDayRecord(UUID id, UUID batchId, LocalDate examinationDate) {}
