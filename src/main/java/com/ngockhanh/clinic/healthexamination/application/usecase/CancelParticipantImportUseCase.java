package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelParticipantImportUseCase {
  private final java.time.Clock clock;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationImportJobRepository jobs;
  private final ParticipantImportAuditWriter auditWriter;

  @Transactional
  public ParticipantImportSummaryResponse execute(
      UUID organizationId, UUID batchId, UUID importId, UUID actorUserId, long expectedVersion) {
    log.debug("Cancel participant import: importId={}, batchId={}", importId, batchId);

    if (organizationId == null || batchId == null || importId == null || actorUserId == null) {
      throw new IllegalArgumentException("Participant import cancellation details are required");
    }
    AggregateId organization = AggregateId.of(organizationId);
    AggregateId batch = AggregateId.of(batchId);
    AggregateId importJobId = AggregateId.of(importId);

    batches
        .findByIdAndOrganizationId(batch, organization)
        .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));

    HealthExaminationImportJob job =
        jobs.findByIdAndBatchIdForUpdate(importJobId, batch)
            .orElseThrow(() -> new ResourceNotFoundException("Participant import"));

    if (job.type() != ImportType.ORGANIZATION_PARTICIPANT) {
      throw new BusinessRuleException("Only participant-list imports can be canceled here") {};
    }

    if (job.status() == ImportStatus.CANCELLED) {
      return ParticipantImportSummaryResponse.from(job, clock.instant());
    }

    if (job.rowVersion() != expectedVersion)
      throw new com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException();
    ImportStatus previousStatus = job.status();
    job.cancel(clock.instant());

    jobs.save(job);

    Instant occurredAt = clock.instant();

    auditWriter.record(
        new AuditEntry(
            UuidV7Generator.generate(),
            actorUserId,
            occurredAt,
            "PARTICIPANT_ROSTER_IMPORT_CANCELLED",
            job.id().value(),
            new ParticipantImportAuditWriter.Snapshot(
                previousStatus, expectedVersion, job.rows().size()),
            new ParticipantImportAuditWriter.Snapshot(
                job.status(), job.rowVersion(), job.rows().size())));
    log.info("Participant import cancellation recorded: importId={}", importId);
    return ParticipantImportSummaryResponse.from(job, clock.instant());
  }
}
