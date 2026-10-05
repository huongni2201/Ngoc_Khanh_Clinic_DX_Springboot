package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantDayAllocator;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.*;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateParticipantImportPreviewUseCase {
  private final java.time.Clock clock;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationImportJobRepository jobs;
  private final HealthExaminationBatchParticipantRepository participants;
  private final ParticipantImportAuditWriter audit;
  private final ParticipantDayAllocator dayAllocator;

  @Transactional
  public ParticipantImportSummaryResponse execute(
      UUID organizationId,
      UUID batchId,
      UUID importId,
      UUID actor,
      long expectedVersion,
      List<UUID> selectedDays,
      Map<Integer, UUID> assignments) {
    log.debug("Revise participant import preview: importId={}, batchId={}", importId, batchId);
    var batch =
        batches
            .findByIdAndOrganizationIdForUpdate(
                AggregateId.of(batchId), AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    if (!batch.status().allowsRosterImport())
      throw new BusinessRuleException("Roster import is not allowed for this batch state") {};
    var job =
        jobs.findByIdAndBatchIdForUpdate(AggregateId.of(importId), batch.id())
            .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
    if (job.rowVersion() != expectedVersion) throw new ConcurrentUpdateException();
    job.requireEditable(clock.instant());
    var days = dayAllocator.selectedDays(batch.days(), selectedDays);
    if (!new java.util.HashSet<>(job.selectedBatchDayIds())
        .equals(
            selectedDays.stream()
                .map(AggregateId::of)
                .collect(java.util.stream.Collectors.toSet())))
      dayAllocator.assignDays(job.rows(), days, participants.activeCountsByDay(batch.id()));
    var rows =
        job.rows().stream()
            .collect(java.util.stream.Collectors.toMap(r -> r.getRowNumber(), r -> r));
    if (assignments != null)
      assignments.forEach(
          (number, day) -> {
            if (!rows.containsKey(number) || !selectedDays.contains(day))
              throw new IllegalArgumentException("Assignment is outside this import preview");
            rows.get(number).assignDay(AggregateId.of(day));
          });
    job.reviseDays(selectedDays.stream().map(AggregateId::of).toList(), clock.instant());
    jobs.save(job);
    audit.record(
        new AuditEntry(
            UuidV7Generator.generate(),
            actor,
            clock.instant(),
            "PARTICIPANT_ROSTER_IMPORT_PREVIEW_CHANGED",
            importId,
            new ParticipantImportAuditWriter.Snapshot(
                job.status(), expectedVersion, job.rows().size()),
            new ParticipantImportAuditWriter.Snapshot(
                job.status(), job.rowVersion(), job.rows().size())));
    log.info(
        "Participant import preview revision recorded: importId={}, rowVersion={}",
        importId,
        job.rowVersion());
    return ParticipantImportSummaryResponse.from(job, clock.instant());
  }
}
