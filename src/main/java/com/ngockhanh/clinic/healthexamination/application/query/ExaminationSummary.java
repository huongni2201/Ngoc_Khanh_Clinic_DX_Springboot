package com.ngockhanh.clinic.healthexamination.application.query;

/**
 * Counters of the active Participants of one agreement, by derived attendance, scheduling and
 * reconciliation state.
 *
 * @param unconfirmed Participants with no attendance recorded yet
 * @param attended Participants who attended at least one session
 * @param noShow Participants who missed a session and have none left to attend
 * @param unscheduled Participants who are in no session at all
 * @param pendingReconciliation Participants who attended and whose services are not reconciled yet
 */
public record ExaminationSummary(
    long registered,
    long unconfirmed,
    long attended,
    long noShow,
    long unscheduled,
    long reconciled,
    long pendingReconciliation) {}
