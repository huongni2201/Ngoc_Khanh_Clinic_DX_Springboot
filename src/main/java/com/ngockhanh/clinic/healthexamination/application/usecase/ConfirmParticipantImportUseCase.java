package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportConfirmResponse;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantDayAllocator;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.*;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmParticipantImportUseCase {
  private final java.time.Clock clock;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationImportJobRepository jobs;
  private final HealthExaminationBatchParticipantRepository participants;
  private final ParticipantImportAuditWriter audit;
  private final ParticipantDayAllocator dayAllocator;

  @Transactional
  public ParticipantImportConfirmResponse execute(
      UUID organizationId, UUID batchId, UUID importId, UUID actor, long expectedVersion) {
    var batch =
        batches
            .findByIdAndOrganizationIdForUpdate(
                AggregateId.of(batchId), AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    var job =
        jobs.findByIdAndBatchIdForUpdate(AggregateId.of(importId), batch.id())
            .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
    if (job.isConfirmed()) return ParticipantImportConfirmResponse.from(job);
    if (job.rowVersion() != expectedVersion) throw new ConcurrentUpdateException();
    if (!batch.status().allowsRosterImport())
      throw new BusinessRuleException("Roster import is not allowed for this batch state") {};
    Instant now = clock.instant();
    job.requireEditable(now);
    dayAllocator.selectedDays(
        batch.days(), job.selectedBatchDayIds().stream().map(AggregateId::value).toList());
    var duplicates =
        participants.existingIdentificationNumbers(
            batch.id(), job.rows().stream().map(r -> r.getIdentificationNumber()).toList());
    if (!duplicates.isEmpty())
      throw new ConcurrentUpdateException(
          "IMPORT_PREVIEW_STALE",
          "An identification number is already in this batch; upload a corrected roster");
    var created =
        job.rows().stream()
            .map(
                r -> {
                  var id = AggregateId.of(UuidV7Generator.generate());
                  var p =
                      HealthExaminationBatchParticipant.create(
                          id,
                          batch.id(),
                          r.getBatchDayId(),
                          new Roster(
                              r.getParticipantCode(),
                              r.getFullName(),
                              r.getDateOfBirth(),
                              r.getSex(),
                              r.getIdentificationNumber(),
                              r.getPhone(),
                              r.getEmail(),
                              r.getDepartmentName(),
                              r.getPositionName()),
                          job.id(),
                          r.getRowNumber(),
                          now);
                  r.resolve(id);
                  return p;
                })
            .toList();
    try {
      participants.insertAll(created);
    } catch (DuplicateKeyException conflict) {
      throw new ConcurrentUpdateException(
          "IMPORT_PREVIEW_STALE", "An identification number is already in this batch");
    }
    job.confirm(AggregateId.of(actor), now);
    jobs.save(job);
    audit.record(
        new AuditEntry(
            UuidV7Generator.generate(),
            actor,
            now,
            "PARTICIPANT_ROSTER_IMPORT_CONFIRMED",
            importId,
            new ParticipantImportAuditWriter.Snapshot(
                com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus.VALIDATED,
                expectedVersion,
                job.rows().size()),
            new ParticipantImportAuditWriter.Snapshot(
                job.status(), job.rowVersion(), job.rows().size())));
    log.info(
        "Participant import confirmation recorded: importId={}, batchId={}, rows={}",
        importId,
        batchId,
        created.size());
    return ParticipantImportConfirmResponse.from(job);
  }
}
