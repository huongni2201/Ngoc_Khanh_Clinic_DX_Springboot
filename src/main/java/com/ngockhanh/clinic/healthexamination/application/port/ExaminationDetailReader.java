package com.ngockhanh.clinic.healthexamination.application.port;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailPage;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationSummary;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentAggregates;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read port of the examination detail matrix, its counters and the payment summary of one batch.
 * It reads projections and never restores aggregates. Every method returns empty when the batch
 * does not belong to the organization or is deleted.
 */
public interface ExaminationDetailReader {
  /** Reads one page of Participant rows with the batch services each one performed. */
  Optional<ExaminationDetailPage> readPage(
      UUID organizationId, UUID batchId, ExaminationDetailCriteria criteria);

  /** Counts the active Participants of the batch by attendance and reconciliation state. */
  Optional<ExaminationSummary> summarize(UUID organizationId, UUID batchId);

  /**
   * Reads every active Participant of the batch in export order (planned day, code with missing
   * codes last, name, identifier) with the batch services each one performed.
   */
  Optional<List<ExaminationDetailRow>> readAllActive(UUID organizationId, UUID batchId);

  /**
   * Counts active Participants and aggregates their performed services by batch service and price
   * snapshot. Rows of cancelled Participants and rows that are not performed are excluded.
   */
  Optional<PaymentAggregates> readPaymentAggregates(UUID organizationId, UUID batchId);
}
