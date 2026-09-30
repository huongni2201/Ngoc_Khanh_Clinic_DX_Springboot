package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.catalog.application.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;

import java.util.*;

import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchDraftEditor {
  private final ServiceCatalogQuery catalog;

  public List<HealthExaminationBatchService> services(
      AggregateId batchId,
      List<BatchConfigurationCommand.ServicePrice> items,
      List<HealthExaminationBatchService> existing) {
    if (items == null || items.isEmpty())
      throw new IllegalArgumentException("Batch services are required");
    Set<UUID> requested = new HashSet<>();
    for (var item : items) {
      if (item == null || item.serviceId() == null || !requested.add(item.serviceId()))
        throw new BusinessRuleException("Duplicate or missing service");
      new Money(item.negotiatedUnitPrice(), "VND");
    }
    Map<UUID, ServiceCatalogQuery.Service> found = new HashMap<>();
    catalog.findByIds(requested).forEach(s -> found.put(s.id(), s));
    Map<UUID, HealthExaminationBatchService> previous = new HashMap<>();
    existing.forEach(s -> previous.put(s.serviceId().value(), s));
    List<HealthExaminationBatchService> result = new ArrayList<>();
    for (var item : items) {
      var old = previous.get(item.serviceId());
      var service = found.get(item.serviceId());
      // Existing snapshots survive catalog edits or retirement; only newly selected items need
      // eligibility.
      if (old == null
          && (service == null || !service.active() || !service.healthExaminationEligible()))
        throw new BusinessRuleException("Service is not eligible for health examination");
      result.add(
          HealthExaminationBatchService.create(
              old == null ? new AggregateId(UuidV7Generator.generate()) : old.id(),
              new AggregateId(item.serviceId()),
              batchId,
              old == null ? service.code() : old.serviceCode(),
              old == null ? service.name() : old.serviceName(),
              new Money(item.negotiatedUnitPrice(), "VND"),
              old == null ? null : old.templateVersionId(),
              result.size() + 1,
              old == null ? "ACTIVE" : old.status()));
    }
    return List.copyOf(result);
  }

  static ExaminationSite site(BatchConfigurationCommand c) {
    if (c == null || c.examinationSiteType() == null)
      throw new IllegalArgumentException("Batch configuration is required");
    return new ExaminationSite(
        com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType.valueOf(
            c.examinationSiteType()),
        c.examinationSiteName(),
        c.examinationSiteAddress());
  }
}
