package com.ngockhanh.clinic.document.application.port;

import java.util.UUID;

/** A file row to insert into {@code public.files}. */
public record NewFile(
    UUID id,
    String storageProvider,
    String storageKey,
    String originalFilename,
    String contentType,
    long sizeBytes,
    byte[] checksum) {}
