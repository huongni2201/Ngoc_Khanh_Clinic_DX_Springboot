package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** SQL projection of a session header joined with the counts of its assignments. */
public record SessionSummaryView(
    UUID id,
    UUID organizationId,
    UUID agreementId,
    LocalDate sessionDate,
    String activity,
    String siteType,
    String siteAddress,
    String status,
    long assignedCount,
    long attendedCount,
    long noShowCount,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
