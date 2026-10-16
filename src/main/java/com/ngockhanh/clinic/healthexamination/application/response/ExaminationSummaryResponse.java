package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationSummary;

/**
 * Counters of the active Participants of an agreement; cancelled Participants are never counted.
 */
public record ExaminationSummaryResponse(
    long registered,
    long unconfirmed,
    long attended,
    long noShow,
    long unscheduled,
    long reconciled,
    long pendingReconciliation) {
  public static ExaminationSummaryResponse from(ExaminationSummary summary) {
    return new ExaminationSummaryResponse(
        summary.registered(),
        summary.unconfirmed(),
        summary.attended(),
        summary.noShow(),
        summary.unscheduled(),
        summary.reconciled(),
        summary.pendingReconciliation());
  }
}
