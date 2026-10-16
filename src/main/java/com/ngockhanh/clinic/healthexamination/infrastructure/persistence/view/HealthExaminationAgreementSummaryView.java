package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row of the agreement list query: an agreement header with its validity period. It is a SQL
 * projection, not a table record, and is mapped to the repository port's read contract.
 */
public record HealthExaminationAgreementSummaryView(
    UUID id,
    String agreementCode,
    String agreementName,
    LocalDate validFrom,
    LocalDate validTo,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
