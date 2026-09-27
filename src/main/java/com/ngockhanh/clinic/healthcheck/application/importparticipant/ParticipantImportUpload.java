package com.ngockhanh.clinic.healthcheck.application.importparticipant;

import java.util.UUID;

public record ParticipantImportUpload(
        UUID organizationId,
        UUID batchId,
        UUID actorUserId,
        String fileName,
        String mimeType,
        byte[] content) {
}
