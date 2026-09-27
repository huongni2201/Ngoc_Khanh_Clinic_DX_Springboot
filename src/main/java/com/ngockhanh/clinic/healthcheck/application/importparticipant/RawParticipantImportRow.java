package com.ngockhanh.clinic.healthcheck.application.importparticipant;

public record RawParticipantImportRow(
        int rowNumber,
        String participantCode,
        String departmentName,
        String jobTitle,
        String occupation,
        RawAdministrativeSnapshot snapshot) {
}
