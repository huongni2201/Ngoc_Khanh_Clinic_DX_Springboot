package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailPage;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationSummary;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentAggregates;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.ExaminationDetailMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ExaminationCountView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ExaminationDetailRowView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.PaymentParticipantCountView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.PaymentSessionCountView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.PerformedServiceView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Reads the examination detail matrix, its counters, its export rows and the payment aggregates
 * with projections: no aggregate restore and no N+1 queries. A page costs a count, a page query and
 * one query for the performed services of that page.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisExaminationDetailReader implements ExaminationDetailReader {
  private final ExaminationDetailMyBatisMapper mapper;

  @Override
  public Optional<ExaminationDetailPage> readPage(
      UUID organizationId, UUID agreementId, ExaminationDetailCriteria criteria) {
    if (!mapper.agreementInScope(organizationId, agreementId)) return Optional.empty();
    long total =
        mapper.countRows(
            agreementId,
            criteria.searchPattern(),
            criteria.rosterStatus(),
            criteria.attendanceStatus(),
            criteria.reconciliationStatus());
    if (total == 0 || criteria.offset() >= total)
      return Optional.of(new ExaminationDetailPage(List.of(), total));
    List<ExaminationDetailRowView> views =
        mapper.findRows(
            agreementId,
            criteria.searchPattern(),
            criteria.rosterStatus(),
            criteria.attendanceStatus(),
            criteria.reconciliationStatus(),
            criteria.sortKey(),
            criteria.sortBy(),
            criteria.offset(),
            criteria.limit());
    Map<UUID, List<UUID>> performed =
        group(
            views.isEmpty()
                ? List.of()
                : mapper.findPerformedServices(
                    views.stream().map(ExaminationDetailRowView::id).toList()));
    List<ExaminationDetailRow> items = new ArrayList<>(views.size());
    for (ExaminationDetailRowView view : views) items.add(row(view, performed));
    return Optional.of(new ExaminationDetailPage(items, total));
  }

  @Override
  public Optional<ExaminationSummary> summarize(UUID organizationId, UUID agreementId) {
    if (!mapper.agreementInScope(organizationId, agreementId)) return Optional.empty();
    ExaminationCountView counts = mapper.countActiveParticipants(agreementId);
    return Optional.of(
        new ExaminationSummary(
            counts.registered(),
            counts.unconfirmed(),
            counts.attended(),
            counts.noShow(),
            counts.unscheduled(),
            counts.reconciled(),
            counts.pendingReconciliation()));
  }

  @Override
  public Optional<List<ExaminationDetailRow>> readAllActive(UUID organizationId, UUID agreementId) {
    if (!mapper.agreementInScope(organizationId, agreementId)) return Optional.empty();
    Map<UUID, List<UUID>> performed =
        group(mapper.findPerformedServicesOfActiveParticipants(agreementId));
    List<ExaminationDetailRow> rows = new ArrayList<>();
    for (ExaminationDetailRowView view : mapper.findActiveRowsForExport(agreementId))
      rows.add(row(view, performed));
    return Optional.of(rows);
  }

  @Override
  public Optional<ExaminationDetailRow> readOne(
      UUID organizationId, UUID agreementId, UUID participantId) {
    if (!mapper.agreementInScope(organizationId, agreementId)) return Optional.empty();
    ExaminationDetailRowView view = mapper.findRow(agreementId, participantId);
    if (view == null) return Optional.empty();
    Map<UUID, List<UUID>> performed = group(mapper.findPerformedServices(List.of(view.id())));
    return Optional.of(row(view, performed));
  }

  @Override
  public Optional<PaymentAggregates> readPaymentAggregates(
      UUID organizationId, UUID agreementId, LocalDate from, LocalDate to) {
    if (!mapper.agreementInScope(organizationId, agreementId)) return Optional.empty();
    PaymentParticipantCountView participantCounts =
        mapper.countPaymentParticipants(agreementId, from, to);
    PaymentSessionCountView sessionCounts = mapper.countPaymentSessions(agreementId, from, to);
    List<PaymentAggregates.PerformedService> lines =
        mapper.aggregatePerformedServices(agreementId, from, to).stream()
            .map(
                view ->
                    new PaymentAggregates.PerformedService(
                        view.agreementServiceId(), view.unitPrice(), view.examinedCount()))
            .toList();
    return Optional.of(
        new PaymentAggregates(
            new PaymentAggregates.ParticipantCounts(
                participantCounts.registered(),
                participantCounts.attended(),
                participantCounts.reconciled(),
                sessionCounts.sessions(),
                sessionCounts.visits()),
            lines));
  }

  private static Map<UUID, List<UUID>> group(List<PerformedServiceView> performed) {
    Map<UUID, List<UUID>> byParticipant = new HashMap<>();
    for (PerformedServiceView view : performed)
      byParticipant
          .computeIfAbsent(view.participantId(), key -> new ArrayList<>())
          .add(view.agreementServiceId());
    return byParticipant;
  }

  private static ExaminationDetailRow row(
      ExaminationDetailRowView view, Map<UUID, List<UUID>> performed) {
    return new ExaminationDetailRow(
        view.id(),
        view.participantCode(),
        view.fullName(),
        view.dateOfBirth(),
        view.sex(),
        view.identificationNumber(),
        view.departmentName(),
        view.positionName(),
        view.nextSessionDate(),
        view.attendanceStatus(),
        view.lastAttendedDate(),
        view.reconciliationStatus(),
        performed.getOrDefault(view.id(), List.of()),
        view.rowVersion());
  }
}
