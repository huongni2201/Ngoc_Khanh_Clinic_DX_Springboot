package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.response.*;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantDayAllocator;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreValidatedParticipantImportUseCase {
  private final java.time.Clock clock;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationImportJobRepository jobs;
  private final HealthExaminationBatchParticipantRepository participants;
  private final ParticipantImportAuditWriter audit;
  private final ParticipantDayAllocator dayAllocator;

  @Transactional
  public ParticipantImportUploadResponse execute(
      UUID organizationId,
      UUID batchId,
      UUID actorId,
      List<UUID> selectedDays,
      List<HealthExaminationImportRow> rows) {
    var batch =
        batches
            .findByIdAndOrganizationIdForUpdate(
                AggregateId.of(batchId), AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    if (!batch.status().allowsRosterImport())
      throw new BusinessRuleException("Roster import is not allowed for this batch state") {};
    var days = dayAllocator.selectedDays(batch.days(), selectedDays);
    var counts = new HashMap<String, Integer>();
    rows.stream()
        .filter(r -> r.getIdentificationNumber() != null)
        .forEach(r -> counts.merge(r.getIdentificationNumber().value(), 1, Integer::sum));
    var existing =
        participants.existingIdentificationNumbers(
            batch.id(),
            rows.stream()
                .filter(HealthExaminationImportRow::isValid)
                .map(HealthExaminationImportRow::getIdentificationNumber)
                .distinct()
                .toList());
    rows.forEach(
        r -> {
          if (r.getIdentificationNumber() != null
              && counts.get(r.getIdentificationNumber().value()) > 1) r.reject("DUPLICATE_IN_FILE");
          if (r.getIdentificationNumber() != null && existing.contains(r.getIdentificationNumber()))
            r.reject("DUPLICATE_IN_BATCH");
        });
    if (rows.stream().anyMatch(r -> !r.isValid()))
      return new ParticipantImportUploadResponse(
          null,
          "REJECTED",
          0,
          rows.size(),
          selectedDays,
          rows.stream().map(ParticipantImportRowResponse::from).toList());
    dayAllocator.assignDays(rows, days, participants.activeCountsByDay(batch.id()));
    Instant now = clock.instant();
    var job =
        new HealthExaminationImportJob(
            AggregateId.of(UuidV7Generator.generate()),
            batch.id(),
            AggregateId.of(actorId),
            now,
            selectedDays.stream().map(AggregateId::of).toList(),
            rows,
            ImportStatus.VALIDATED,
            null,
            null,
            null,
            null,
            0,
            false,
            null);
    jobs.save(job);
    audit.record(
        new AuditEntry(
            UuidV7Generator.generate(),
            actorId,
            now,
            "PARTICIPANT_ROSTER_IMPORT_VALIDATED",
            job.id().value(),
            new ParticipantImportAuditWriter.Snapshot(null, 0, 0),
            new ParticipantImportAuditWriter.Snapshot(
                job.status(), job.rowVersion(), rows.size())));
    log.info(
        "Participant import validated: importId={}, batchId={}, rows={}",
        job.id().value(),
        batchId,
        rows.size());
    return new ParticipantImportUploadResponse(
        job.id().value(),
        job.status().name(),
        0,
        rows.size(),
        selectedDays,
        rows.stream().map(ParticipantImportRowResponse::from).toList());
  }
}
