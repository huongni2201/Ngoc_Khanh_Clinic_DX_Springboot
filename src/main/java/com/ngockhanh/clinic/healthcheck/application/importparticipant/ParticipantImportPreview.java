package com.ngockhanh.clinic.healthcheck.application.importparticipant;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.enums.ImportStatus;

public record ParticipantImportPreview(
        UUID importId,
        UUID batchId,
        ImportStatus status,
        int totalRows,
        int validRows,
        int errorRows,
        List<Row> rows) {
    public ParticipantImportPreview {
        rows = List.copyOf(rows);
    }

    public record Row(int rowNumber, String participantCode, String fullName, String sex,
                      LocalDate dateOfBirth, String identificationNumber, List<String> errorCodes) {
        public Row {
            errorCodes = List.copyOf(errorCodes);
        }

        public boolean valid() {
            return errorCodes.isEmpty();
        }
    }
}
