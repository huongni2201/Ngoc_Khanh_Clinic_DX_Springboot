package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.StartHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts a draft health examination batch: DRAFT becomes READY (UC-071).
 *
 * <p>A started batch keeps its configuration (days, services, prices); only a draft can be edited
 * or deleted afterwards. Participants can still be added, edited or cancelled, and the blank
 * examination forms can be printed. The batch header is locked while the transition runs, and the
 * transition and its audit event are written in one transaction, so an audit failure rolls the
 * transition back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartHealthExaminationBatchUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final ServiceCatalogQuery catalog;
  private final AuditWriter audit;

  /**
   * Starts a draft batch when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it must exist and be active
   * @param batchId batch identifier
   * @param command carries the row version the caller last read
   * @param actor authenticated account starting the batch
   * @return the batch as stored, READY with the incremented row version
   * @throws IllegalArgumentException when an argument is null or the row version is negative
   * @throws ResourceNotFoundException when the organization or the batch does not exist, or the
   *     batch is deleted
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the organization is inactive or the batch is not a draft
   */
  @Transactional
  public BatchDetailResponse execute(
      UUID organizationId, UUID batchId, StartHealthExaminationBatchCommand command, UUID actor) {
    if (organizationId == null || batchId == null || command == null || actor == null)
      throw new IllegalArgumentException(
          "Organization ID, batch ID, command, and actor are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (organization.status() != OrganizationStatus.ACTIVE)
      throw new DomainRuleViolation("A batch of an inactive organization cannot be started");
    var current =
        batches
            .findDetails(organizationId, batchId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    var batch = current.batch();
    if (expectedVersion != batch.rowVersion()) throw new ConcurrentUpdateException();

    batch.markReady();
    batches.updateStatus(batch, BatchStatus.DRAFT, expectedVersion);
    var stored =
        batches
            .findDetails(organizationId, batchId, false)
            .orElseThrow(() -> new IllegalStateException("Started batch could not be read back"));
    var after = toResponse(stored);
    audit.record(
        actor,
        "START_HEALTH_EXAMINATION_BATCH",
        "HEALTH_EXAMINATION_BATCH",
        batchId,
        Map.of(
            "organizationId", organizationId,
            "status", BatchStatus.DRAFT.name(),
            "rowVersion", expectedVersion),
        Map.of(
            "organizationId", organizationId,
            "status", after.status(),
            "rowVersion", after.rowVersion()));
    log.info(
        "Health examination batch start pending commit: organizationId={}, batchId={}",
        organizationId,
        batchId);
    return after;
  }

  private BatchDetailResponse toResponse(BatchDetails details) {
    Set<UUID> serviceIds = new HashSet<>();
    details.batch().services().forEach(service -> serviceIds.add(service.serviceId().value()));
    Map<UUID, ServiceCatalogQuery.Service> byId = new HashMap<>();
    if (!serviceIds.isEmpty())
      catalog.findByIds(serviceIds).forEach(service -> byId.put(service.id(), service));
    return BatchDetailResponse.from(details, byId);
  }
}
