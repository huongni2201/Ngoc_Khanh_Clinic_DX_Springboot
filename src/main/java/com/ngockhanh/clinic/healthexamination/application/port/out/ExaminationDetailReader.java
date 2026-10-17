package com.ngockhanh.clinic.healthexamination.application.port.out;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailPage;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationSummary;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentAggregates;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read port of the examination detail matrix, its counters and the payment summary of one
 * agreement. It reads projections and never restores aggregates. Every method returns empty when
 * the agreement does not belong to the organization or is deleted.
 */
public interface ExaminationDetailReader {
  /** Reads one page of Participant rows with the agreement services each one performed. */
  Optional<ExaminationDetailPage> readPage(
      UUID organizationId, UUID agreementId, ExaminationDetailCriteria criteria);

  /**
   * Counts the active Participants of the agreement by derived attendance, scheduling and
   * reconciliation state.
   */
  Optional<ExaminationSummary> summarize(UUID organizationId, UUID agreementId);

  /**
   * Reads every active Participant of the agreement in export order (next session date, code with
   * missing codes last, name, identifier) with the agreement services each one performed.
   */
  Optional<List<ExaminationDetailRow>> readAllActive(UUID organizationId, UUID agreementId);

  /**
   * Reads one Participant of the agreement, whatever its roster status, with the agreement services
   * it performed. Empty when the agreement is not in scope or the Participant is not in the
   * agreement.
   */
  Optional<ExaminationDetailRow> readOne(UUID organizationId, UUID agreementId, UUID participantId);

  /**
   * Counts the active Participants in scope and aggregates their performed services by agreement
   * service and price snapshot. Rows of cancelled Participants and rows that are not performed are
   * excluded. With a range, only Participants expected or attended in a session of that range are
   * in scope.
   *
   * @param from first session date of the range, or null for no lower bound
   * @param to last session date of the range, or null for no upper bound
   */
  Optional<PaymentAggregates> readPaymentAggregates(
      UUID organizationId, UUID agreementId, LocalDate from, LocalDate to);
}
