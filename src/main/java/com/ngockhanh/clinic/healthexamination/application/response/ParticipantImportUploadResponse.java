package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.List;
import java.util.UUID;

public record ParticipantImportUploadResponse(
    UUID importId,
    String status,
    long rowVersion,
    int totalRows,
    List<UUID> selectedBatchDayIds,
    List<ParticipantImportRowResponse> rows) {}
