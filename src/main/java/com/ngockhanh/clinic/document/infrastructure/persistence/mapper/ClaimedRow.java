package com.ngockhanh.clinic.document.infrastructure.persistence.mapper;

import java.util.UUID;

/** A job just taken by a worker. */
public record ClaimedRow(
    UUID id,
    int attemptCount,
    String documentType,
    UUID requestedBy,
    String context,
    String recordIds) {}
