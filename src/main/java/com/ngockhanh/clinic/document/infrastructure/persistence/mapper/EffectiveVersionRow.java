package com.ngockhanh.clinic.document.infrastructure.persistence.mapper;

import java.util.UUID;

/** Result row of {@code DocumentTemplateMapper.findEffective}: a version joined to its file. */
public record EffectiveVersionRow(
    UUID versionId,
    int versionNo,
    int contractVersion,
    String storageProvider,
    String storageKey,
    byte[] checksum,
    Integer pagesPerRecord) {}
