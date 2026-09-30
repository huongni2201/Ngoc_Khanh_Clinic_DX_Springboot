package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;

public record ParticipantImportUploadResponse(
        UUID importId,
        String status,
        int headerRowNumber,
        List<String> headers,
        Map<ParticipantImportField, Integer> suggestedMapping) {
}
