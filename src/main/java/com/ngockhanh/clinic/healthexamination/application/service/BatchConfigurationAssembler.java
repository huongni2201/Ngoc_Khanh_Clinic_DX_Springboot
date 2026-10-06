package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
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
import org.springframework.stereotype.Component;

/**
 * Turns a {@link BatchConfiguration} into days and services with identifiers and price snapshots.
 *
 * <p>For a new batch every day and service gets a new identifier and a reference-price snapshot
 * from the catalog. For an update, days and services that are kept keep their identifier, and a
 * kept service also keeps its snapshot and active flag; only services added by the update are
 * looked up in the catalog. The assembler has no transaction and writes no audit.
 */
@Component
@RequiredArgsConstructor
public class BatchConfigurationAssembler {
  private static final String CURRENCY = "VND";

  private final ServiceCatalogQuery catalog;

  /**
   * Assembles the configuration of a batch that is about to be created.
   *
   * @throws IllegalArgumentException when dates, site or prices are invalid
   * @throws DomainRuleViolation when a service is repeated, missing or inactive in the catalog
   */
  public AssembledConfiguration forCreate(AggregateId batchId, BatchConfiguration configuration) {
    return assemble(batchId, configuration, List.of(), List.of());
  }

  /**
   * Assembles the replacement configuration of an existing batch.
   *
   * @throws IllegalArgumentException when dates, site or prices are invalid
   * @throws DomainRuleViolation when a service is repeated, or a newly added service is missing or
   *     inactive in the catalog
   */
  public AssembledConfiguration forUpdate(
      HealthExaminationBatch current, BatchConfiguration configuration) {
    if (current == null) throw new IllegalArgumentException("Batch is required");
    return assemble(current.id(), configuration, current.days(), current.services());
  }

  private AssembledConfiguration assemble(
      AggregateId batchId,
      BatchConfiguration configuration,
      List<HealthExaminationBatchDay> existingDays,
      List<HealthExaminationBatchService> existingServices) {
    if (batchId == null || configuration == null)
      throw new IllegalArgumentException("Batch configuration is required");
    var site =
        new ExaminationSite(
            siteType(configuration.examinationSiteType()),
            configuration.examinationSiteName(),
            configuration.examinationSiteAddress());
    return new AssembledConfiguration(
        configuration.batchCode(),
        configuration.batchName(),
        site,
        days(configuration.examinationDates(), existingDays),
        services(batchId, configuration.services(), existingServices));
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
}
