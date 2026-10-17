package com.ngockhanh.clinic.healthexamination.application.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read contract of the payment summary of one agreement: how many Participants are in scope and how
 * many were recorded as performed per agreement service and price snapshot.
 *
 * <p>The scope is the active Participants of the agreement; with a date range, only those expected
 * or attended in a session of that range.
 */
public record PaymentAggregates(ParticipantCounts counts, List<PerformedService> lines) {
  public PaymentAggregates {
    lines = List.copyOf(lines);
  }

  /**
   * Counters of the scope.
   *
   * @param registered Participants in scope
   * @param attended distinct Participants who attended at least one session
   * @param reconciled Participants whose services are reconciled
   * @param sessions distinct sessions the Participants in scope are in
   * @param visits attended assignments, so one Participant who came twice counts twice
   */
  public record ParticipantCounts(
      long registered, long attended, long reconciled, long sessions, long visits) {}

  /**
   * Performed rows of Participants in scope that share an agreement service and a historical price.
   *
   * @param unitPrice price snapshot stored on the rows, not the current negotiated price
   * @param examinedCount number of Participants it was performed for
   */
  public record PerformedService(
      UUID agreementServiceId, BigDecimal unitPrice, long examinedCount) {}
}
