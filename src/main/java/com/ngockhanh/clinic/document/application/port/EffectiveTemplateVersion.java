package com.ngockhanh.clinic.document.application.port;

import java.util.UUID;

/**
 * The published template version in effect, with the location of its Word file.
 *
 * @param versionId template version
 * @param versionNo number of the version inside its template
 * @param contractVersion contract version the template was made against
 * @param storageProvider where the file is kept
 * @param storageKey location inside the provider
 * @param checksum SHA-256 of the file
 * @param pagesPerRecord pages one record occupies, or {@code null} when it varies
 */
public record EffectiveTemplateVersion(
    UUID versionId,
    int versionNo,
    int contractVersion,
    String storageProvider,
    String storageKey,
    byte[] checksum,
    Integer pagesPerRecord) {}
