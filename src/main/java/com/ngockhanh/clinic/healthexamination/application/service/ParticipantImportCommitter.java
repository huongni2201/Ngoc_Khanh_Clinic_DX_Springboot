package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantWorkbook;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.integration.application.imports.CommittedImportRow;
import com.ngockhanh.clinic.integration.application.imports.ImportReceipt;
import com.ngockhanh.clinic.integration.application.imports.ImportRequestIdentity;
import com.ngockhanh.clinic.integration.application.imports.ImportReservation;
import com.ngockhanh.clinic.integration.application.imports.ParticipantImportStore;
import com.ngockhanh.clinic.integration.application.imports.StagedParticipantRow;
import com.ngockhanh.clinic.integration.application.imports.ValidatedParticipantImport;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
 * Commits a validated Participant import atomically.
 *
 * <p>It is a separate bean so the use case calls it through the Spring proxy. In one transaction it
 * locks the agreement, reserves or replays the idempotency key, then (for a new import only)
 * rechecks the organization and agreement state, checks the expected agreement version, rejects
 * identification numbers that already belong to the agreement (whatever their roster status),
 * records the import job and its rows, inserts every Participant, confirms the job, completes the
 * key and writes the audit event. Any failure rolls back all of it, so a rejected import leaves
 * nothing behind. Lock order is agreement, then idempotency key, then (when the import also
 * schedules) the session, then import job, then Participant and assignment writes. No Patient,
 * Encounter or ParticipantService is created or looked up.
 *
 * <p>When the request names a session, the session must be open and serve the agreement, and every
 * new Participant is assigned to it in the same transaction, so an import never leaves people
 * created but unscheduled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantImportCommitter {
  static final int INSERT_CHUNK_SIZE = 200;
  private static final long JOB_VERSION_AFTER_CREATE = 0L;
  private static final String COMMITTED_RESOURCE_TYPE = "HEALTH_EXAMINATION_PARTICIPANT";

  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final HealthExaminationParticipantRepository participants;
  private final HealthExaminationSessionRepository sessions;
  private final SessionAssignmentRepository assignments;
  private final ParticipantImportStore imports;
  private final AuditWriter audit;
  private final Clock clock;

  /** A row whose content already passed the domain invariants. */
  public record Row(int rowNumber, Roster roster) {}

  /**
   * Everything the commit needs.
   *
   * @param fingerprint 32-byte request fingerprint used for idempotency
   * @param actorId authenticated account performing the import
   * @param sessionId session the new Participants are scheduled into, or null to schedule nobody
   */
  public record Request(
      UUID organizationId,
      UUID agreementId,
      long expectedRowVersion,
      UUID idempotencyKey,
      byte[] fingerprint,
      List<Row> rows,
      UUID actorId,
      UUID sessionId) {
    public Request {
      fingerprint = fingerprint.clone();
      rows = List.copyOf(rows);
    }

    /** An import that schedules nobody. */
    public Request(
        UUID organizationId,
        UUID agreementId,
        long expectedRowVersion,
        UUID idempotencyKey,
        byte[] fingerprint,
        List<Row> rows,
        UUID actorId) {
      this(
          organizationId,
          agreementId,
          expectedRowVersion,
          idempotencyKey,
          fingerprint,
          rows,
          actorId,
          null);
    }
  }

  /** The receipt and whether it was replayed from an earlier identical request. */
  public record Outcome(ImportReceipt receipt, boolean replayed) {}

  /**
   * Imports the rows, or replays the stored receipt of an identical completed request.
   *
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   * @throws ConflictException when the agreement does not accept imports, the key was used for
   *     another request, or an identification number already belongs to the agreement
   * @throws ConcurrentUpdateException when the expected agreement version is stale
   * @throws DomainRuleViolation when a named session is cancelled or serves another agreement
   */
  @Transactional
  public Outcome commit(Request request) {
    var details =
        agreements
            .findDetails(request.organizationId(), request.agreementId(), true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    var agreement = details.agreement();
    var organization =
        organizations
            .findById(AggregateId.of(request.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));

    // A completed identical request replays before the lifecycle and version checks: the import
    // succeeded once, so a retry must return the stored receipt even if the organization has since
    // become inactive or the agreement has moved on. Only a new import is subject to those rules.
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
      case ImportReservation.Replay replay -> {
        return new Outcome(replay.receipt(), true);
      }
      case ImportReservation.New fresh -> reservationId = fresh.reservationId();
    }

    if (organization.status() != OrganizationStatus.ACTIVE || !agreement.acceptsParticipantImport())
      throw new ConflictException("Agreement does not accept Participant imports");
    if (agreement.rowVersion() != request.expectedRowVersion())
      throw new ConcurrentUpdateException();
    AggregateId agreementId = AggregateId.of(request.agreementId());
    Set<String> existing = new HashSet<>();
    for (IdentificationNumber identity :
        participants.findExistingIdentities(
            agreementId,
            request.rows().stream().map(row -> row.roster().identificationNumber()).toList()))
      existing.add(identity.value());
    for (Row row : request.rows())
      if (existing.contains(row.roster().identificationNumber().value()))
        throw new ConflictException(
            "Participant identity already exists in this agreement at row " + row.rowNumber());

    HealthExaminationSession session = null;
    if (request.sessionId() != null) {
      session =
          sessions
              .findById(request.organizationId(), request.sessionId(), true)
              .orElseThrow(() -> new ResourceNotFoundException("Session"));
      session.requireOpen();
      session.requireServes(agreementId);
    }

    UUID jobId =
        imports.createValidatedJob(
            new ValidatedParticipantImport(
                request.agreementId(),
                request.actorId(),
                ParticipantWorkbook.TEMPLATE_VERSION,
                request.expectedRowVersion(),
                request.rows().stream().map(ParticipantImportCommitter::staged).toList()));

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    AggregateId importJobId = AggregateId.of(jobId);
    List<HealthExaminationParticipant> created = new ArrayList<>(request.rows().size());
    List<CommittedImportRow> committed = new ArrayList<>(request.rows().size());
    // The agreement row is locked above, so the count the codes continue from cannot move until
    // commit.
    long existingCount = participants.countInAgreement(agreementId);
    int rowIndex = 0;
    for (Row row : request.rows()) {
      UUID participantId = UuidV7Generator.generate();
      String code = String.format("%s-%04d", agreement.code(), existingCount + ++rowIndex);
      created.add(
          HealthExaminationParticipant.create(
              AggregateId.of(participantId),
              AggregateId.of(request.organizationId()),
              agreementId,
              row.roster().withParticipantCode(code),
              importJobId,
              row.rowNumber(),
              now));
      committed.add(
          new CommittedImportRow(row.rowNumber(), COMMITTED_RESOURCE_TYPE, participantId));
    }
    for (int from = 0; from < created.size(); from += INSERT_CHUNK_SIZE)
      participants.insertMany(
          created.subList(from, Math.min(from + INSERT_CHUNK_SIZE, created.size())));
    log.info(
        "Participant insert executed; commit pending: agreementId={}, importJobId={}, count={}",
        request.agreementId(),
        jobId,
        created.size());
    if (session != null) schedule(request, session, created, now);

    var receipt =
        new ImportReceipt(jobId, request.agreementId(), created.size(), created.size(), now);
    imports.markRowsCommitted(jobId, committed);
    imports.confirmJob(jobId, JOB_VERSION_AFTER_CREATE, receipt);
    imports.completeRequest(reservationId, receipt);
    audit.record(
        request.actorId(),
        "IMPORT_PARTICIPANTS",
        "HEALTH_EXAMINATION_AGREEMENT",
        request.agreementId(),
        null,
        Map.of(
            "organizationId", request.organizationId(),
            "agreementId", request.agreementId(),
            "importJobId", jobId,
            "totalRows", receipt.totalRows(),
            "createdCount", receipt.createdCount(),
            "expectedRowVersion", request.expectedRowVersion()));
    return new Outcome(receipt, false);
  }

  /** Puts every Participant created by this import into the session; one audit event records it. */
  private void schedule(
      Request request,
      HealthExaminationSession session,
      List<HealthExaminationParticipant> created,
      Instant now) {
    AggregateId actor = AggregateId.of(request.actorId());
    List<SessionAssignment> scheduled = new ArrayList<>(created.size());
    for (HealthExaminationParticipant participant : created)
      scheduled.add(
          SessionAssignment.assign(
              AggregateId.of(UuidV7Generator.generate()),
              AggregateId.of(request.organizationId()),
              participant.getId(),
              session.id(),
              actor,
              now));
    for (int from = 0; from < scheduled.size(); from += INSERT_CHUNK_SIZE)
      assignments.insertMany(
          scheduled.subList(from, Math.min(from + INSERT_CHUNK_SIZE, scheduled.size())));
    audit.record(
        request.actorId(),
        "ASSIGN_PARTICIPANTS_TO_SESSION",
        "HEALTH_EXAMINATION_SESSION",
        session.id().value(),
        null,
        Map.of(
            "organizationId", request.organizationId(),
            "requested", scheduled.size(),
            "assigned", scheduled.size(),
            "skipped", 0));
  }

  private static StagedParticipantRow staged(Row row) {
    Roster roster = row.roster();
    return new StagedParticipantRow(
        row.rowNumber(),
        roster.fullName(),
        roster.dateOfBirth(),
        roster.sex(),
        roster.identificationNumber().value(),
        roster.identificationIssueDate(),
        roster.identificationIssuePlace(),
        roster.ethnicity(),
        roster.phone(),
        roster.email(),
        roster.address(),
        roster.workplace(),
        roster.departmentName(),
        roster.positionName(),
        roster.note());
  }
}
