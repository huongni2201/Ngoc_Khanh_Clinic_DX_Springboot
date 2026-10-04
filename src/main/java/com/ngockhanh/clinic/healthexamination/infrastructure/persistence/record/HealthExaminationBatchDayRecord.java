package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.LocalDate;
import java.util.UUID;

public record HealthExaminationBatchDayRecord(UUID id, UUID batchId, LocalDate examinationDate) {}
