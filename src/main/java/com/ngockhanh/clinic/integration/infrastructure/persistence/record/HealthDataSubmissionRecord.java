package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.health_data_submissions}. */
public record HealthDataSubmissionRecord(
    UUID id,
    UUID patientId,
    UUID encounterId,
    String submissionType,
    String status,
    String payloadVersion,
    Instant preparedAt,
    Instant validatedAt,
    Instant submittedAt,
    String externalReference,
    Instant createdAt) {}
