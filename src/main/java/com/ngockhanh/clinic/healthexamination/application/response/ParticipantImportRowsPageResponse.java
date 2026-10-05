package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.List;
import java.util.UUID;

public record ParticipantImportRowsPageResponse(
    UUID importId, int page, int size, long totalRows, List<ParticipantImportRowResponse> rows) {}
