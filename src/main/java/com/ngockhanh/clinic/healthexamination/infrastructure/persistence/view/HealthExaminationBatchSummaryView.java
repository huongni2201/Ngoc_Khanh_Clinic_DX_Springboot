package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row of the batch list query: a batch header joined with the bounds of its examination days. It
 * is a SQL projection, not a table record, and is mapped to the repository port's read contract.
 */
public record HealthExaminationBatchSummaryView(
    UUID id,
    String batchCode,
    String batchName,
    LocalDate startDate,
    LocalDate endDate,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
