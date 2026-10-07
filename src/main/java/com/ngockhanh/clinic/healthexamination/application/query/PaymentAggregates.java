package com.ngockhanh.clinic.healthexamination.application.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read contract of the payment summary of one batch: how many active Participants it has and how
 * many were recorded as performed per batch service and price snapshot.
 */
public record PaymentAggregates(ParticipantCounts counts, List<PerformedService> lines) {
  public PaymentAggregates {
    lines = List.copyOf(lines);
  }

  /** Active Participants of the batch: registered, attended and with reconciled services. */
  public record ParticipantCounts(long registered, long attended, long reconciled) {}

  /**
   * Performed rows of active Participants that share a batch service and a historical price.
   *
   * @param unitPrice price snapshot stored on the rows, not the current negotiated price
   * @param examinedCount number of Participants it was performed for
   */
  public record PerformedService(UUID batchServiceId, BigDecimal unitPrice, long examinedCount) {}
}
