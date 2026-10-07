package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationSummary;

/** Counters of the active Participants of a batch; cancelled Participants are never counted. */
public record ExaminationSummaryResponse(
    long registered,
    long unconfirmed,
    long attended,
    long absent,
    long reconciled,
    long pendingReconciliation) {
  public static ExaminationSummaryResponse from(ExaminationSummary summary) {
    return new ExaminationSummaryResponse(
        summary.registered(),
        summary.unconfirmed(),
        summary.attended(),
        summary.absent(),
        summary.reconciled(),
        summary.pendingReconciliation());
  }
}
