package com.ngockhanh.clinic.document.infrastructure.persistence.mapper;

import java.time.Instant;
import java.util.UUID;

/**
 * A job row; JSON columns and the id array come back as text and are decoded by the repository.
 */
public record RenderJobRow(
    UUID id,
    String documentType,
    UUID requestedBy,
    UUID idempotencyKey,
    String context,
    String recordIds,
    String status,
    int totalCount,
    int processedCount,
    String skipped,
    String errorCode,
    Instant createdAt,
    Instant finishedAt,
    Instant expiresAt) {}
