package com.ngockhanh.clinic.healthexamination.application.query;

/** Counters of the active Participants of one batch, by attendance and reconciliation state. */
public record ExaminationSummary(
    long registered,
    long unconfirmed,
    long attended,
    long absent,
    long reconciled,
    long pendingReconciliation) {}
