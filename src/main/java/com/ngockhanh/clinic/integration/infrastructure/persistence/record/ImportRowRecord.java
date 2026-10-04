package com.ngockhanh.clinic.integration.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** A row in public.import_rows. */
public record ImportRowRecord(
    UUID id,
    UUID jobId,
    int rowNumber,
    String normalizedPayload,
    String previewMetadata,
    String committedResourceType,
    UUID committedResourceId,
    Instant createdAt) {}
