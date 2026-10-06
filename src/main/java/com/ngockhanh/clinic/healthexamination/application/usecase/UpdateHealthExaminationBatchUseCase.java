package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AssembledConfiguration;
import com.ngockhanh.clinic.healthexamination.application.service.BatchConfigurationAssembler;
import com.ngockhanh.clinic.healthexamination.application.service.BatchDetailResponseMapper;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
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
 * Replaces the whole configuration of a draft health examination batch using optimistic locking.
 *
 * <p>The batch header is locked while the update runs. Days and services that are kept keep their
 * identifiers and price snapshots. A day or service that is removed must not be referenced by any
 * Participant. The changes and the audit event are written in one transaction, so a stale version,
 * a conflict or an audit failure rolls back everything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateHealthExaminationBatchUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final BatchConfigurationAssembler assembler;
  private final BatchDetailResponseMapper responses;
  private final AuditWriter audit;

  /**
   * Updates a draft batch when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it may be inactive
   * @param batchId batch identifier
   * @param command full replacement configuration and the expected row version
   * @param actor authenticated account performing the update
   * @return the batch as stored, with the incremented row version
   * @throws IllegalArgumentException when an argument or the configuration is invalid
   * @throws ResourceNotFoundException when the organization or the batch does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the batch is not a draft, a new service is not available, or
   *     a removed day or service is referenced by a Participant
   */
  @Transactional
  public BatchDetailResponse execute(
      UUID organizationId, UUID batchId, UpdateHealthExaminationBatchCommand command, UUID actor) {
    if (organizationId == null
        || batchId == null
        || command == null
        || command.configuration() == null
        || actor == null)
      throw new IllegalArgumentException(
          "Organization ID, batch ID, configuration, and updater are required");
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

    var before = responses.toResponse(current);
    var configuration = assembler.forUpdate(batch, command.configuration());
    rejectReferencedRemovals(batchId, batch.days(), batch.services(), configuration);
    batch.updateDraft(
        configuration.batchCode(),
        configuration.batchName(),
        configuration.site(),
        configuration.days(),
        configuration.services());

    batches.update(batch, expectedVersion);
    var stored =
        batches
            .findDetails(organizationId, batchId, false)
            .orElseThrow(() -> new IllegalStateException("Updated batch could not be read back"));
    var after = responses.toResponse(stored);
    audit.record(
        actor,
        "UPDATE_HEALTH_EXAMINATION_BATCH",
        "HEALTH_EXAMINATION_BATCH",
        batchId,
        Map.of(
            "organizationId", organizationId,
            "status", before.status(),
            "rowVersion", before.rowVersion(),
            "configuration", before.auditSummary()),
        Map.of(
            "organizationId", organizationId,
            "status", after.status(),
            "rowVersion", after.rowVersion(),
            "configuration", after.auditSummary()));
    log.info(
        "Health examination batch update pending commit: organizationId={}, batchId={}, days={},"
            + " services={}",
        organizationId,
        batchId,
        after.days().size(),
        after.services().size());
    return after;
  }

  private void rejectReferencedRemovals(
      UUID batchId,
      List<HealthExaminationBatchDay> currentDays,
      List<HealthExaminationBatchService> currentServices,
      AssembledConfiguration target) {
    Set<UUID> keptDays = new HashSet<>();
    target.days().forEach(day -> keptDays.add(day.id()));
    Set<UUID> removedDays = new HashSet<>();
    currentDays.forEach(
        day -> {
          if (!keptDays.contains(day.id())) removedDays.add(day.id());
        });
    if (!batches.findReferencedDayIds(batchId, removedDays).isEmpty())
      throw new DomainRuleViolation("A removed examination day has Participants");

    Set<UUID> keptServices = new HashSet<>();
    target.services().forEach(service -> keptServices.add(service.id().value()));
    Set<UUID> removedServices = new HashSet<>();
    currentServices.forEach(
        service -> {
          if (!keptServices.contains(service.id().value()))
            removedServices.add(service.id().value());
        });
    if (!batches.findReferencedBatchServiceIds(batchId, removedServices).isEmpty())
      throw new DomainRuleViolation("A removed batch service is used by Participants");
  }
}
