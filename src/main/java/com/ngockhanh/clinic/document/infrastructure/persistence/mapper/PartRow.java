package com.ngockhanh.clinic.document.infrastructure.persistence.mapper;

import java.util.UUID;

/** A merged PDF part of a job joined to its file. */
public record PartRow(
    int partNo,
    UUID fileId,
    int recordCount,
    long sizeBytes,
    String storageProvider,
    String storageKey,
    byte[] checksum) {}
