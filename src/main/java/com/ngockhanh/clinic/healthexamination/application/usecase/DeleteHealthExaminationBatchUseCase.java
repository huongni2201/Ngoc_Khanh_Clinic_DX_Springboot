package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.integration.application.query.BatchHistoryQuery;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft-deletes a draft health examination batch that has no Participant and no integration history.
 *
 * <p>Nothing is physically removed: the batch row, its days and services, and its batch code are
 * kept, and the batch disappears from every read. The batch header is locked while the checks and
 * the deletion run. The deletion and its audit event are written in one transaction, so an audit
 * failure rolls the deletion back. Deleting an already deleted batch reports it as not found.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteHealthExaminationBatchUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final BatchHistoryQuery batchHistory;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Soft-deletes a batch when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it may be inactive
   * @param batchId batch identifier
   * @param command carries the row version the caller last read
   * @param actor authenticated account performing the deletion
   * @throws IllegalArgumentException when an argument is null or the row version is negative
   * @throws ResourceNotFoundException when the organization or the batch does not exist, or the
   *     batch is already deleted
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the batch is not a draft, has any Participant (whatever its
   *     roster status), or is referenced by integration history
   */
  @Transactional
  public void execute(
      UUID organizationId, UUID batchId, DeleteHealthExaminationBatchCommand command, UUID actor) {
    if (organizationId == null || batchId == null || command == null || actor == null)
      throw new IllegalArgumentException(
          "Organization ID, batch ID, command, and actor are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var current =
        batches
            .findDetails(organizationId, batchId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    var batch = current.batch();
    if (expectedVersion != batch.rowVersion()) throw new ConcurrentUpdateException();
    batch.requireDraft();
    if (batches.hasParticipants(batchId))
      throw new DomainRuleViolation("A batch with Participants cannot be deleted");
    if (batchHistory.hasBatchReferences(batchId))
      throw new DomainRuleViolation("A batch with integration history cannot be deleted");

    Instant deletedAt = Instant.now(clock);
    batch.softDelete(deletedAt);
    batches.softDelete(batch, expectedVersion);
    audit.record(
        actor,
        "DELETE_HEALTH_EXAMINATION_BATCH",
        "HEALTH_EXAMINATION_BATCH",
        batchId,
        Map.of(
            "organizationId", organizationId,
            "status", batch.status().name(),
            "rowVersion", expectedVersion),
        Map.of(
            "organizationId", organizationId,
            "status", batch.status().name(),
            "rowVersion", expectedVersion + 1,
            "deletedAt", deletedAt.toString()));
    log.info(
        "Health examination batch deletion pending commit: organizationId={}, batchId={}",
        organizationId,
        batchId);
  }
}
