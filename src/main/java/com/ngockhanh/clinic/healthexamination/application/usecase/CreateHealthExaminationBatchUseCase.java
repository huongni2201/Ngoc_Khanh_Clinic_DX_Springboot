package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a draft health examination batch for an active organization.
 *
 * <p>The header, its days and services, and the audit event are written in one transaction, so a
 * failure of any part rolls back all of it. A concurrent create with the same batch code loses at
 * the database unique constraint, which surfaces as a duplicate-key conflict.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateHealthExaminationBatchUseCase {
  private static final String CURRENCY = "VND";

  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final ServiceCatalogQuery catalog;
  private final AuditWriter audit;

  /**
   * Creates a batch with status {@code DRAFT} and row version 0.
   *
   * @param organizationId owning organization
   * @param command batch configuration
   * @param actor authenticated account creating the batch
   * @return the batch as stored, including timestamps
   * @throws IllegalArgumentException when an argument or the configuration is invalid
   * @throws ResourceNotFoundException when the organization does not exist
   * @throws DomainRuleViolation when the organization is inactive or a service is not available
   */
  @Transactional
  public BatchDetailResponse execute(
      UUID organizationId, CreateHealthExaminationBatchCommand command, UUID actor) {
    if (organizationId == null
        || command == null
        || command.configuration() == null
        || actor == null)
      throw new IllegalArgumentException(
          "Organization ID, configuration, and creator are required");
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (organization.status() != OrganizationStatus.ACTIVE)
      throw new DomainRuleViolation("Organization is not active");

    var batchId = new AggregateId(UuidV7Generator.generate());
    var configuration = command.configuration();
    var site =
        new ExaminationSite(
            siteType(configuration.examinationSiteType()),
            configuration.examinationSiteName(),
            configuration.examinationSiteAddress());
    var days = days(configuration.examinationDates());
    var services = services(batchId, configuration.services());
    var batch =
        HealthExaminationBatch.createDraft(
            batchId,
            organization.id(),
            configuration.batchCode(),
            configuration.batchName(),
            site,
            days,
            services);
    batches.insert(batch, actor);
    var stored =
        batches
            .findDetails(organizationId, batchId.value(), false)
            .orElseThrow(() -> new IllegalStateException("Created batch could not be read back"));
    var response = toResponse(stored);
    audit.record(
        actor,
        "CREATE_HEALTH_EXAMINATION_BATCH",
        "HEALTH_EXAMINATION_BATCH",
        batchId.value(),
        null,
        Map.of(
            "organizationId", organizationId,
            "status", response.status(),
            "rowVersion", response.rowVersion(),
            "configuration", response.auditSummary()));
    log.info(
        "Health examination batch creation pending commit: organizationId={}, batchId={}, days={},"
            + " services={}",
        organizationId,
        batchId.value(),
        response.days().size(),
        response.services().size());
    return response;
  }

  private static ExaminationSiteType siteType(String value) {
    if (value == null) throw new IllegalArgumentException("Examination site type is required");
    return ExaminationSiteType.valueOf(value);
  }

  private static List<HealthExaminationBatchDay> days(List<LocalDate> dates) {
    if (dates == null || dates.isEmpty())
      throw new IllegalArgumentException("At least one examination date is required");
    Set<LocalDate> seen = new HashSet<>();
    List<HealthExaminationBatchDay> days = new ArrayList<>();
    for (LocalDate date : dates) {
      if (date == null || !seen.add(date))
        throw new IllegalArgumentException("Examination dates must be present and distinct");
      days.add(new HealthExaminationBatchDay(UuidV7Generator.generate(), date));
    }
    return days;
  }

  private List<HealthExaminationBatchService> services(
      AggregateId batchId, List<BatchConfiguration.ServicePrice> requested) {
    if (requested == null || requested.isEmpty())
      throw new IllegalArgumentException("At least one service is required");

    Set<UUID> seen = new HashSet<>();
    for (var item : requested) {
      if (item == null || item.serviceId() == null || item.negotiatedPrice() == null)
        throw new IllegalArgumentException("Service and negotiated price are required");
      if (!seen.add(item.serviceId())) throw new DomainRuleViolation("Duplicate batch service");
    }
    Map<UUID, ServiceCatalogQuery.Service> catalogServices = new HashMap<>();
    for (var service : catalog.findByIds(seen)) catalogServices.put(service.id(), service);

    List<HealthExaminationBatchService> services = new ArrayList<>();
    int displayOrder = 1;
    for (var item : requested) {
      Money negotiated = new Money(item.negotiatedPrice(), CURRENCY);
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
