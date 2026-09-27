package com.ngockhanh.clinic.healthcheck.api.importparticipant;

public record ParticipantImportFileError(String result, int code, String errorCode, String message) {
    public ParticipantImportFileError(String errorCode) {
        this("NG", 400, errorCode, "Participant workbook is invalid");
    }
}
