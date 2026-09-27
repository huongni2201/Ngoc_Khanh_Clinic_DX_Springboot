package com.ngockhanh.clinic.healthcheck.application.importparticipant;

public final class ParticipantImportWorkbookException extends RuntimeException {
    private final String code;

    public ParticipantImportWorkbookException(String code) {
        super("Invalid participant import workbook");
        this.code = code;
    }

    public ParticipantImportWorkbookException(String code, Throwable cause) {
        super("Invalid participant import workbook", cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
