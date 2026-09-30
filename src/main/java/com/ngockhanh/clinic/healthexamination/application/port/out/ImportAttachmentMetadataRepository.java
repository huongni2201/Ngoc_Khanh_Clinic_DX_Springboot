package com.ngockhanh.clinic.healthexamination.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ImportAttachmentMetadataRepository {
    Optional<ImportAttachmentMetadata> findById(UUID id);
    void save(ImportAttachmentMetadata attachment);

    record ImportAttachmentMetadata(
            UUID id,
            UUID importJobId,
            UUID createdByUserId,
            String storageKey,
            String fileName,
            String mimeType,
            long sizeBytes,
            String sha256,
            Instant createdAt) {
    }
}
