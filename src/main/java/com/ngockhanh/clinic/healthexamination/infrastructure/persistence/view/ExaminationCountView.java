package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

/** SQL projection of the counters of the active Participants of one batch. */
public record ExaminationCountView(
    long registered,
    long unconfirmed,
    long attended,
    long absent,
    long reconciled,
    long pendingReconciliation) {}
