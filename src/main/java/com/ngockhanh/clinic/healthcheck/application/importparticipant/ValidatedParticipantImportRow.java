package com.ngockhanh.clinic.healthcheck.application.importparticipant;

import java.util.List;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;

public record ValidatedParticipantImportRow(
        int rowNumber,
        String participantCode,
        String departmentName,
        String jobTitle,
        String occupation,
        AdministrativeSnapshot snapshot,
        List<ImportValidationError> errors) {

    public ValidatedParticipantImportRow {
        errors = List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
