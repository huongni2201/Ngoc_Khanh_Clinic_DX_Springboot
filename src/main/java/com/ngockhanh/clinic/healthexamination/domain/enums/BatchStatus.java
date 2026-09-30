package com.ngockhanh.clinic.healthexamination.domain.enums;

public enum BatchStatus {
    DRAFT, READY, IN_PROGRESS, RESULT_PROCESSING, FINALIZED, CLOSED, CANCELED, DELETED;

    public boolean allowsRosterImport() {
        return this == DRAFT || this == READY || this == IN_PROGRESS;
    }
}
