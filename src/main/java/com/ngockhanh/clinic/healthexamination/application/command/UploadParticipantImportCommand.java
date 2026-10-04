package com.ngockhanh.clinic.healthexamination.application.command;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public record UploadParticipantImportCommand(
    UUID organizationId,
    UUID batchId,
    UUID actorUserId,
    String fileName,
    String contentType,
    long sizeBytes,
    InputStream content,
    List<UUID> selectedBatchDayIds) {}
