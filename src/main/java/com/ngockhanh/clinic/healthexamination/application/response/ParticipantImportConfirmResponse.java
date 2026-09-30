package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.UUID;

public record ParticipantImportConfirmResponse(
        UUID importId,
        String status,
        int importedRows,
        int createdRows,
        int updatedRows,
        int unchangedRows) {
}
