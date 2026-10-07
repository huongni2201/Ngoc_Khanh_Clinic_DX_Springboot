package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.integration.application.imports.CommittedImportRow;
import com.ngockhanh.clinic.integration.application.imports.ImportRequestIdentity;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationImportStore;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReceipt;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReservation;
import com.ngockhanh.clinic.integration.application.imports.StagedReconciliationRow;
import com.ngockhanh.clinic.integration.application.imports.ValidatedServiceReconciliationImport;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits a validated examination detail import atomically.
 *
 * <p>It is a separate bean so the use case calls it through the Spring proxy. In one transaction it
 * locks the batch, reserves or replays the idempotency key, then (for a new import only) rechecks
 * the organization and batch state, checks that the file declares exactly the services of the
 * batch, checks the actual dates, locks every Participant of the file in identifier order, checks
 * each one is in the batch, active and unchanged since the export, applies the file to each of them
 * through {@link ExaminationDetailReconciler}, saves the ones that changed, records the import job
 * and its rows, confirms the job, completes the key and writes the audit event. Any failure rolls
 * back all of it, so a rejected import leaves nothing behind. Lock order is batch, then idempotency
 * key, then Participants. No Patient, Encounter, clinical result or ServiceRequest is created or
 * looked up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExaminationDetailImportCommitter {
  /** Business time zone used to decide what "today" is for an actual examination date. */
  static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private static final long JOB_VERSION_AFTER_CREATE = 0L;
  private static final String COMMITTED_RESOURCE_TYPE = "HEALTH_EXAMINATION_BATCH_PARTICIPANT";

  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationBatchParticipantRepository participants;
  private final ServiceReconciliationImportStore imports;
  private final AuditWriter audit;
  private final Clock clock;

  private final ExaminationDetailReconciler reconciler = new ExaminationDetailReconciler();

  /**
   * Everything the commit needs.
   *
   * @param declaredServiceIds batch services the file declares in its key row
   * @param fingerprint 32-byte request fingerprint used for idempotency
   * @param actorId authenticated account performing the import
   */
  public record Request(
      UUID organizationId,
      UUID batchId,
      UUID idempotencyKey,
      byte[] fingerprint,
      int templateVersion,
      Set<UUID> declaredServiceIds,
      List<ExaminationDetailImportRow> rows,
      UUID actorId) {
    public Request {
      fingerprint = fingerprint.clone();
      declaredServiceIds = Set.copyOf(declaredServiceIds);
      rows = List.copyOf(rows);
    }
  }

  /** The receipt and whether it was replayed from an earlier identical request. */
  public record Outcome(ServiceReconciliationReceipt receipt, boolean replayed) {}

  /**
   * Imports the rows, or replays the stored receipt of an identical completed request.
   *
   * @throws ResourceNotFoundException when the organization or the batch is not found
   * @throws ApplicationException of type {@code INVALID_INPUT} when the file does not declare the
   *     services of this batch or an actual date is outside the batch dates and today
   * @throws ConflictException when the batch does not accept changes, a Participant is not in the
   *     batch or is cancelled, a newly marked service is no longer offered, or the key was used for
   *     another request
   * @throws ConcurrentUpdateException when a Participant changed after the file was exported
   */
  @Transactional
  public Outcome commit(Request request) {
    var details =
        batches
            .findDetails(request.organizationId(), request.batchId(), true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    HealthExaminationBatch batch = details.batch();
    var organization =
        organizations
            .findById(AggregateId.of(request.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));

    // A completed identical request replays before the lifecycle checks: the import succeeded once,
    // so a retry must return the stored receipt even if the organization has since become inactive
    // or the batch has moved on. Only a new import is subject to those rules.
    var reservation =
        imports.reserve(
            new ImportRequestIdentity(
                request.organizationId(),
                request.batchId(),
                request.actorId(),
                request.idempotencyKey(),
                request.fingerprint()));
    UUID reservationId;
    switch (reservation) {
      case ServiceReconciliationReservation.Replay replay -> {
        return new Outcome(replay.receipt(), true);
      }
      case ServiceReconciliationReservation.New fresh -> reservationId = fresh.reservationId();
    }

    if (organization.status() != OrganizationStatus.ACTIVE || !batch.acceptsParticipantChanges())
      throw new ConflictException("Batch does not accept examination detail changes");
    requireSameServices(batch, request.declaredServiceIds());
    requireValidDates(batch, request.rows());

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    AggregateId actor = AggregateId.of(request.actorId());
    Map<UUID, HealthExaminationBatchParticipant> locked = lockParticipants(request);
    Map<UUID, LocalDate> plannedDates = new HashMap<>();
    for (HealthExaminationBatchDay day : batch.days())
      plannedDates.put(day.id(), day.examinationDate());

    int updated = 0;
    int performedItems = 0;
    List<CommittedImportRow> committed = new ArrayList<>();
    for (ExaminationDetailImportRow row : request.rows()) {
      HealthExaminationBatchParticipant participant = locked.get(row.participantId());
      if (participant == null)
        throw new ConflictException("Row " + row.rowNumber() + ": participant is not in this batch");
      if (participant.getRosterStatus() != RosterStatus.ACTIVE)
        throw new ConflictException("Row " + row.rowNumber() + ": participant is cancelled");
      if (participant.getRowVersion() != row.rowVersion())
        throw new ConcurrentUpdateException(
            "PARTICIPANT_CHANGED_AFTER_EXPORT",
            "Row " + row.rowNumber() + ": participant was changed after export; export again");
    }
    requirePlannedDatesNotInFuture(request.rows(), locked, plannedDates);
    for (ExaminationDetailImportRow row : request.rows()) {
      HealthExaminationBatchParticipant participant = locked.get(row.participantId());
      performedItems += row.performedServiceIds().size();
      var result =
          reconciler.apply(
              participant,
              batch,
              row,
              actor,
              now,
              plannedDates.get(participant.getBatchDayId().value()));
      if (result == ExaminationDetailReconciler.Result.UPDATED) {
        participants.save(participant, row.rowVersion());
        updated++;
        committed.add(
            new CommittedImportRow(row.rowNumber(), COMMITTED_RESOURCE_TYPE, row.participantId()));
      }
    }
    log.debug(
        "Participant reconciliation executed; commit pending: batchId={}, rows={}, updated={}",
        request.batchId(),
        request.rows().size(),
        updated);

    UUID jobId =
        imports.createValidatedJob(
            new ValidatedServiceReconciliationImport(
                request.batchId(),
                request.actorId(),
                request.templateVersion(),
                request.rows().stream().map(ExaminationDetailImportCommitter::staged).toList()));
    var receipt =
        new ServiceReconciliationReceipt(
            jobId,
            request.batchId(),
            request.rows().size(),
            updated,
            request.rows().size() - updated,
            performedItems,
            now);
    imports.markRowsCommitted(jobId, committed);
    imports.confirmJob(jobId, JOB_VERSION_AFTER_CREATE, receipt);
    imports.completeRequest(reservationId, receipt);
    audit.record(
        request.actorId(),
        "IMPORT_EXAMINATION_DETAILS",
        "HEALTH_EXAMINATION_BATCH",
        request.batchId(),
        null,
        Map.of(
            "organizationId", request.organizationId(),
            "batchId", request.batchId(),
            "importJobId", jobId,
            "totalRows", receipt.totalRows(),
            "updatedParticipants", receipt.updatedParticipants(),
            "unchangedParticipants", receipt.unchangedParticipants(),
            "performedItems", receipt.performedItems()));
    return new Outcome(receipt, false);
  }

  /** The file must declare exactly the services of the batch, inactive ones included. */
  private static void requireSameServices(HealthExaminationBatch batch, Set<UUID> declared) {
    Set<UUID> current = new HashSet<>();
    for (HealthExaminationBatchService service : batch.services())
      current.add(service.id().value());
    if (!current.equals(declared))
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          "The file does not match this batch; export it again");
  }

  /**
   * A row that marks services for a Participant who is not yet attended and leaves the actual date
   * blank would be attended on the planned date. That date must not be in the future (business
   * time), the same limit an explicit actual date has; the row must then state the actual date.
   */
  private void requirePlannedDatesNotInFuture(
      List<ExaminationDetailImportRow> rows,
      Map<UUID, HealthExaminationBatchParticipant> locked,
      Map<UUID, LocalDate> plannedDates) {
    LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
    for (ExaminationDetailImportRow row : rows) {
      if (row.actualExaminationDate() != null || row.performedServiceIds().isEmpty()) continue;
      HealthExaminationBatchParticipant participant = locked.get(row.participantId());
      if (participant.getAttendanceStatus() == AttendanceStatus.ATTENDED) continue;
      LocalDate planned = plannedDates.get(participant.getBatchDayId().value());
      if (planned != null && planned.isAfter(today))
        throw new ApplicationException(
            ApplicationException.Type.INVALID_INPUT,
            "Row "
                + row.rowNumber()
                + ": actual_examination_date is required because the planned examination date"
                + " is in the future");
    }
  }

  /** An actual date is at least the first day of the batch and at most today (business time). */
  private void requireValidDates(HealthExaminationBatch batch, List<ExaminationDetailImportRow> rows) {
    LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
    for (ExaminationDetailImportRow row : rows) {
      LocalDate date = row.actualExaminationDate();
      if (date != null && (date.isBefore(batch.startDate()) || date.isAfter(today)))
        throw new ApplicationException(
            ApplicationException.Type.INVALID_INPUT,
            "Row "
                + row.rowNumber()
                + ": actual_examination_date must be between the batch start date and today");
    }
  }

  /** Locks every Participant of the file in one query, whatever batch the identifier was made for. */
  private Map<UUID, HealthExaminationBatchParticipant> lockParticipants(Request request) {
    List<AggregateId> ids = new ArrayList<>(request.rows().size());
    for (ExaminationDetailImportRow row : request.rows()) ids.add(AggregateId.of(row.participantId()));
    Map<UUID, HealthExaminationBatchParticipant> byId = new HashMap<>();
    for (HealthExaminationBatchParticipant participant :
        participants.findManyInBatchForUpdate(AggregateId.of(request.batchId()), ids))
      byId.put(participant.getId().value(), participant);
    return byId;
  }

  private static StagedReconciliationRow staged(ExaminationDetailImportRow row) {
    return new StagedReconciliationRow(
        row.rowNumber(),
        row.participantId(),
        row.performedServiceIds().stream().sorted().toList(),
        row.actualExaminationDate());
  }
}
