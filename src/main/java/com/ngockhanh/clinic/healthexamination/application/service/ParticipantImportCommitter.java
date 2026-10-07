package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantWorkbook;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
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
import java.time.LocalDate;
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
 * Commits a validated Participant import atomically.
 *
 * <p>It is a separate bean so the use case calls it through the Spring proxy. In one transaction it
 * locks the batch, reserves or replays the idempotency key, then (for a new import only) rechecks
 * the organization and batch state, checks the expected batch version, resolves the examination
 * days, rejects identification numbers that already belong to the batch (whatever their roster
 * status), records the import job and its rows, inserts every Participant, confirms the job,
 * completes the key and writes the audit event. Any failure rolls back all of it, so a rejected import leaves nothing behind. Lock order
 * is batch, then idempotency key, then import job, then Participant writes. No Patient, Encounter
 * or ParticipantService is created or looked up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantImportCommitter {
  static final int INSERT_CHUNK_SIZE = 200;
  private static final long JOB_VERSION_AFTER_CREATE = 0L;
  private static final String COMMITTED_RESOURCE_TYPE = "HEALTH_EXAMINATION_BATCH_PARTICIPANT";

  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationBatchParticipantRepository participants;
  private final ParticipantImportStore imports;
  private final AuditWriter audit;
  private final Clock clock;

  /** A row whose content already passed the domain invariants. */
  public record Row(int rowNumber, Roster roster, LocalDate examinationDate) {}

  /**
   * Everything the commit needs.
   *
   * @param fingerprint 32-byte request fingerprint used for idempotency
   * @param actorId authenticated account performing the import
   */
  public record Request(
      UUID organizationId,
      UUID batchId,
      long expectedRowVersion,
      UUID idempotencyKey,
      byte[] fingerprint,
      List<Row> rows,
      UUID actorId) {
    public Request {
      fingerprint = fingerprint.clone();
      rows = List.copyOf(rows);
    }
  }

  /** The receipt and whether it was replayed from an earlier identical request. */
  public record Outcome(ImportReceipt receipt, boolean replayed) {}

  /**
   * Imports the rows, or replays the stored receipt of an identical completed request.
   *
   * @throws ResourceNotFoundException when the organization or the batch is not found
   * @throws ConflictException when the batch does not accept imports, the key was used for another
   *     request, an examination date is not a day of the batch, or an identification number
   *     already belongs to the batch
   * @throws ConcurrentUpdateException when the expected batch version is stale
   */
  @Transactional
  public Outcome commit(Request request) {
    var details =
        batches
            .findDetails(request.organizationId(), request.batchId(), true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    var batch = details.batch();
    var organization =
        organizations
            .findById(AggregateId.of(request.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));

    // A completed identical request replays before the lifecycle and version checks: the import
    // succeeded once, so a retry must return the stored receipt even if the organization has since
    // become inactive or the batch has moved on. Only a new import is subject to those rules.
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
      case ImportReservation.Replay replay -> {
        return new Outcome(replay.receipt(), true);
      }
      case ImportReservation.New fresh -> reservationId = fresh.reservationId();
    }

    if (organization.status() != OrganizationStatus.ACTIVE || !batch.acceptsParticipantImport())
      throw new ConflictException("Batch does not accept Participant imports");
    if (batch.rowVersion() != request.expectedRowVersion()) throw new ConcurrentUpdateException();
    Map<LocalDate, UUID> dayIds = new HashMap<>();
    for (HealthExaminationBatchDay day : batch.days()) dayIds.put(day.examinationDate(), day.id());
    for (Row row : request.rows())
      if (!dayIds.containsKey(row.examinationDate()))
        throw new ConflictException(
            "Row " + row.rowNumber() + ": examination_date is not a day of this batch");

    AggregateId batchId = AggregateId.of(request.batchId());
    Set<String> existing = new HashSet<>();
    for (IdentificationNumber identity :
        participants.findExistingIdentities(
            batchId,
            request.rows().stream().map(row -> row.roster().identificationNumber()).toList()))
      existing.add(identity.value());
    for (Row row : request.rows())
      if (existing.contains(row.roster().identificationNumber().value()))
        throw new ConflictException(
            "Participant identity already exists in this batch at row " + row.rowNumber());

    UUID jobId =
        imports.createValidatedJob(
            new ValidatedParticipantImport(
                request.batchId(),
                request.actorId(),
                ParticipantWorkbook.TEMPLATE_VERSION,
                request.expectedRowVersion(),
                request.rows().stream().map(ParticipantImportCommitter::staged).toList()));

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    AggregateId importJobId = AggregateId.of(jobId);
    List<HealthExaminationBatchParticipant> created = new ArrayList<>(request.rows().size());
    List<CommittedImportRow> committed = new ArrayList<>(request.rows().size());
    for (Row row : request.rows()) {
      UUID participantId = UuidV7Generator.generate();
      created.add(
          HealthExaminationBatchParticipant.create(
              AggregateId.of(participantId),
              batchId,
              AggregateId.of(dayIds.get(row.examinationDate())),
              row.roster(),
              importJobId,
              row.rowNumber(),
              now));
      committed.add(new CommittedImportRow(row.rowNumber(), COMMITTED_RESOURCE_TYPE, participantId));
    }
    for (int from = 0; from < created.size(); from += INSERT_CHUNK_SIZE)
      participants.insertMany(created.subList(from, Math.min(from + INSERT_CHUNK_SIZE, created.size())));
    log.debug(
        "Participant insert executed; commit pending: batchId={}, importJobId={}, count={}",
        request.batchId(),
        jobId,
        created.size());

    var receipt = new ImportReceipt(jobId, request.batchId(), created.size(), created.size(), now);
    imports.markRowsCommitted(jobId, committed);
    imports.confirmJob(jobId, JOB_VERSION_AFTER_CREATE, receipt);
    imports.completeRequest(reservationId, receipt);
    audit.record(
        request.actorId(),
        "IMPORT_BATCH_PARTICIPANTS",
        "HEALTH_EXAMINATION_BATCH",
        request.batchId(),
        null,
        Map.of(
            "organizationId", request.organizationId(),
            "batchId", request.batchId(),
            "importJobId", jobId,
            "totalRows", receipt.totalRows(),
            "createdCount", receipt.createdCount(),
            "expectedRowVersion", request.expectedRowVersion()));
    return new Outcome(receipt, false);
  }

  private static StagedParticipantRow staged(Row row) {
    Roster roster = row.roster();
    return new StagedParticipantRow(
        row.rowNumber(),
        roster.participantCode(),
        roster.fullName(),
        roster.dateOfBirth(),
        roster.sex(),
        roster.identificationNumber().value(),
        roster.phone(),
        roster.email(),
        roster.departmentName(),
        roster.positionName(),
        row.examinationDate());
  }
}
