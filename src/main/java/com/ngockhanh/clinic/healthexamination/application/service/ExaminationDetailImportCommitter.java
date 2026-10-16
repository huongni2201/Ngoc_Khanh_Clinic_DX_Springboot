package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
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
 * locks the agreement, reserves or replays the idempotency key, then (for a new import only)
 * rechecks the organization and agreement state, checks that the file declares exactly the services
 * of the agreement, locks every Participant of the file in identifier order, checks each one is in
 * the agreement, active and unchanged since the export, applies the file to each of them through
 * {@link ExaminationDetailReconciler}, saves the ones that changed, records the import job and its
 * rows, confirms the job, completes the key and writes the audit event. Any failure rolls back all
 * of it, so a rejected import leaves nothing behind. Lock order is agreement, then idempotency key,
 * then Participants. No Patient, Encounter, clinical result, ServiceRequest or attendance is
 * created or looked up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExaminationDetailImportCommitter {
  private static final long JOB_VERSION_AFTER_CREATE = 0L;
  private static final String COMMITTED_RESOURCE_TYPE = "HEALTH_EXAMINATION_PARTICIPANT";

  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final HealthExaminationParticipantRepository participants;
  private final ServiceReconciliationImportStore imports;
  private final AuditWriter audit;
  private final Clock clock;

  private final ExaminationDetailReconciler reconciler = new ExaminationDetailReconciler();

  /**
   * Everything the commit needs.
   *
   * @param declaredServiceIds agreement services the file declares in its key row
   * @param fingerprint 32-byte request fingerprint used for idempotency
   * @param actorId authenticated account performing the import
   */
  public record Request(
      UUID organizationId,
      UUID agreementId,
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
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   * @throws ApplicationException of type {@code INVALID_INPUT} when the file does not declare the
   *     services of this agreement
   * @throws ConflictException when the agreement does not accept changes, a Participant is not in
   *     the agreement or is cancelled, a newly marked service is no longer offered, or the key was
   *     used for another request
   * @throws ConcurrentUpdateException when a Participant changed after the file was exported
   */
  @Transactional
  public Outcome commit(Request request) {
    var details =
        agreements
            .findDetails(request.organizationId(), request.agreementId(), true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    HealthExaminationAgreement agreement = details.agreement();
    var organization =
        organizations
            .findById(AggregateId.of(request.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));

    // A completed identical request replays before the lifecycle checks: the import succeeded once,
    // so a retry must return the stored receipt even if the organization has since become inactive
    // or the agreement has moved on. Only a new import is subject to those rules.
    var reservation =
        imports.reserve(
            new ImportRequestIdentity(
                request.organizationId(),
                request.agreementId(),
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

    if (organization.status() != OrganizationStatus.ACTIVE
        || !agreement.acceptsParticipantChanges())
      throw new ConflictException("Agreement does not accept examination detail changes");
    requireSameServices(agreement, request.declaredServiceIds());

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    AggregateId actor = AggregateId.of(request.actorId());
    Map<UUID, HealthExaminationParticipant> locked = lockParticipants(request);

    int updated = 0;
    int performedItems = 0;
    List<CommittedImportRow> committed = new ArrayList<>();
    for (ExaminationDetailImportRow row : request.rows()) {
      HealthExaminationParticipant participant = locked.get(row.participantId());
      if (participant == null)
        throw new ConflictException(
            "Row " + row.rowNumber() + ": participant is not in this agreement");
      if (participant.getRosterStatus() != RosterStatus.ACTIVE)
        throw new ConflictException("Row " + row.rowNumber() + ": participant is cancelled");
      if (participant.getRowVersion() != row.rowVersion())
        throw new ConcurrentUpdateException(
            "PARTICIPANT_CHANGED_AFTER_EXPORT",
            "Row " + row.rowNumber() + ": participant was changed after export; export again");
    }
    for (ExaminationDetailImportRow row : request.rows()) {
      HealthExaminationParticipant participant = locked.get(row.participantId());
      performedItems += row.performedServiceIds().size();
      var result = reconciler.apply(participant, agreement, row.toEntry(), actor, now);
      if (result == ExaminationDetailReconciler.Result.UPDATED) {
        participants.save(participant, row.rowVersion());
        updated++;
        committed.add(
            new CommittedImportRow(row.rowNumber(), COMMITTED_RESOURCE_TYPE, row.participantId()));
      }
    }
    log.info(
        "Participant reconciliation executed; commit pending: agreementId={}, rows={}, updated={}",
        request.agreementId(),
        request.rows().size(),
        updated);

    UUID jobId =
        imports.createValidatedJob(
            new ValidatedServiceReconciliationImport(
                request.agreementId(),
                request.actorId(),
                request.templateVersion(),
                request.rows().stream().map(ExaminationDetailImportCommitter::staged).toList()));
    var receipt =
        new ServiceReconciliationReceipt(
            jobId,
            request.agreementId(),
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
        "HEALTH_EXAMINATION_AGREEMENT",
        request.agreementId(),
        null,
        Map.of(
            "organizationId", request.organizationId(),
            "agreementId", request.agreementId(),
            "importJobId", jobId,
            "totalRows", receipt.totalRows(),
            "updatedParticipants", receipt.updatedParticipants(),
            "unchangedParticipants", receipt.unchangedParticipants(),
            "performedItems", receipt.performedItems()));
    return new Outcome(receipt, false);
  }

  /** The file must declare exactly the services of the agreement, retired ones included. */
  private static void requireSameServices(
      HealthExaminationAgreement agreement, Set<UUID> declared) {
    Set<UUID> current = new HashSet<>();
    for (HealthExaminationAgreementService service : agreement.services())
      current.add(service.id().value());
    if (!current.equals(declared))
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          "The file does not match this agreement; export it again");
  }

  /**
   * Locks every Participant of the file in one query, whatever agreement the identifier was made
   * for.
   */
  private Map<UUID, HealthExaminationParticipant> lockParticipants(Request request) {
    List<AggregateId> ids = new ArrayList<>(request.rows().size());
    for (ExaminationDetailImportRow row : request.rows())
      ids.add(AggregateId.of(row.participantId()));
    Map<UUID, HealthExaminationParticipant> byId = new HashMap<>();
    for (HealthExaminationParticipant participant :
        participants.findManyInAgreementForUpdate(AggregateId.of(request.agreementId()), ids))
      byId.put(participant.getId().value(), participant);
    return byId;
  }

  private static StagedReconciliationRow staged(ExaminationDetailImportRow row) {
    return new StagedReconciliationRow(
        row.rowNumber(), row.participantId(), row.performedServiceIds().stream().sorted().toList());
  }
}
