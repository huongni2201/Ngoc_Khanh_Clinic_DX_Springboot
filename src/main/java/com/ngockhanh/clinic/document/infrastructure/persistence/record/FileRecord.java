package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.files}. */
@Builder
public record FileRecord(
    UUID id,
    String storageProvider,
    String storageKey,
    String originalFilename,
    String contentType,
    long sizeBytes,
    byte[] checksum,
    Instant createdAt) {}
