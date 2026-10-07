package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private static final String CURRENCY = "VND";

  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final ServiceCatalogQuery catalog;
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
   * @throws DomainRuleViolation when the batch is not a draft, a new service is not available, or a
   *     removed day or service is referenced by a Participant
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

    var before = toResponse(current);
    var configuration = command.configuration();
    var site =
        new ExaminationSite(
            siteType(configuration.examinationSiteType()),
            configuration.examinationSiteName(),
            configuration.examinationSiteAddress());
    var days = days(configuration.examinationDates(), batch.days());
    var services = services(batch.id(), configuration.services(), batch.services());
    rejectReferencedRemovals(batchId, batch.days(), batch.services(), days, services);
    batch.updateDraft(configuration.batchCode(), configuration.batchName(), site, days, services);

    batches.update(batch, expectedVersion);
    var stored =
        batches
            .findDetails(organizationId, batchId, false)
            .orElseThrow(() -> new IllegalStateException("Updated batch could not be read back"));
    var after = toResponse(stored);
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
      List<HealthExaminationBatchDay> targetDays,
      List<HealthExaminationBatchService> targetServices) {
    Set<UUID> keptDays = new HashSet<>();
    targetDays.forEach(day -> keptDays.add(day.id()));
    Set<UUID> removedDays = new HashSet<>();
    currentDays.forEach(
        day -> {
          if (!keptDays.contains(day.id())) removedDays.add(day.id());
        });
    if (!batches.findReferencedDayIds(batchId, removedDays).isEmpty())
      throw new DomainRuleViolation("A removed examination day has Participants");

    Set<UUID> keptServices = new HashSet<>();
    targetServices.forEach(service -> keptServices.add(service.id().value()));
    Set<UUID> removedServices = new HashSet<>();
    currentServices.forEach(
        service -> {
          if (!keptServices.contains(service.id().value()))
            removedServices.add(service.id().value());
        });
    if (!batches.findReferencedBatchServiceIds(batchId, removedServices).isEmpty())
      throw new DomainRuleViolation("A removed batch service is used by Participants");
  }

  private static ExaminationSiteType siteType(String value) {
    if (value == null) throw new IllegalArgumentException("Examination site type is required");
    return ExaminationSiteType.valueOf(value);
  }

  private static List<HealthExaminationBatchDay> days(
      List<LocalDate> dates, List<HealthExaminationBatchDay> existingDays) {
    if (dates == null || dates.isEmpty())
      throw new IllegalArgumentException("At least one examination date is required");
    Map<LocalDate, UUID> existingIds = new HashMap<>();
    for (var day : existingDays) existingIds.put(day.examinationDate(), day.id());
    Set<LocalDate> seen = new HashSet<>();
    List<HealthExaminationBatchDay> days = new ArrayList<>();
    for (LocalDate date : dates) {
      if (date == null || !seen.add(date))
        throw new IllegalArgumentException("Examination dates must be present and distinct");
      UUID id = existingIds.get(date);
      days.add(new HealthExaminationBatchDay(id != null ? id : UuidV7Generator.generate(), date));
    }
    return days;
  }

  private List<HealthExaminationBatchService> services(
      AggregateId batchId,
      List<BatchConfiguration.ServicePrice> requested,
      List<HealthExaminationBatchService> existingServices) {
    if (requested == null || requested.isEmpty())
      throw new IllegalArgumentException("At least one service is required");
    Map<UUID, HealthExaminationBatchService> existing = new HashMap<>();
    for (var service : existingServices) existing.put(service.serviceId().value(), service);

    Set<UUID> seen = new HashSet<>();
    for (var item : requested) {
      if (item == null || item.serviceId() == null || item.negotiatedPrice() == null)
        throw new IllegalArgumentException("Service and negotiated price are required");
      if (!seen.add(item.serviceId())) throw new DomainRuleViolation("Duplicate batch service");
    }
    Set<UUID> added =
        seen.stream().filter(id -> !existing.containsKey(id)).collect(Collectors.toSet());
    Map<UUID, ServiceCatalogQuery.Service> catalogServices = new HashMap<>();
    if (!added.isEmpty())
      for (var service : catalog.findByIds(added)) catalogServices.put(service.id(), service);

    List<HealthExaminationBatchService> services = new ArrayList<>();
    int displayOrder = 1;
    for (var item : requested) {
      Money negotiated = new Money(item.negotiatedPrice(), CURRENCY);
      var kept = existing.get(item.serviceId());
      if (kept != null) {
        services.add(
            new HealthExaminationBatchService(
                kept.id(),
                kept.serviceId(),
                batchId,
                kept.referencePriceSnapshot(),
                negotiated,
                displayOrder++,
                kept.active(),
                kept.rowVersion()));
        continue;
      }
      var found = catalogServices.get(item.serviceId());
      if (found == null || !found.active())
        throw new DomainRuleViolation("Service is not available for a batch");
      services.add(
          new HealthExaminationBatchService(
              new AggregateId(UuidV7Generator.generate()),
              new AggregateId(item.serviceId()),
              batchId,
              new Money(found.unitPrice(), CURRENCY),
              negotiated,
              displayOrder++,
              true,
              0));
    }
    return services;
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
