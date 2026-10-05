package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchDraftEditor {
  private final ServiceCatalogQuery catalog;

  public List<HealthExaminationBatchService> services(
      AggregateId batchId, List<BatchConfigurationCommand.ServicePrice> items) {
    if (items == null || items.isEmpty())
      throw new IllegalArgumentException("Batch services are required");
    Set<UUID> requested = new HashSet<>();
    for (var item : items)
      if (item == null || item.serviceId() == null || !requested.add(item.serviceId()))
        throw new BusinessRuleException("Duplicate or missing service");
    Map<UUID, ServiceCatalogQuery.Service> found = new HashMap<>();
    catalog.findByIds(requested).forEach(s -> found.put(s.id(), s));
    List<HealthExaminationBatchService> result = new ArrayList<>();
    for (var item : items) {
      var service = found.get(item.serviceId());
      if (service == null || !service.active())
        throw new BusinessRuleException("Service is inactive or unavailable");
      result.add(
          new HealthExaminationBatchService(
              new AggregateId(UuidV7Generator.generate()),
              new AggregateId(item.serviceId()),
              batchId,
              new Money(service.unitPrice(), "VND"),
              new Money(item.negotiatedPrice(), "VND"),
              result.size() + 1,
              true,
              0));
    }
    return List.copyOf(result);
  }

  public List<HealthExaminationBatchDay> days(List<LocalDate> dates) {
    if (dates == null
        || dates.isEmpty()
        || dates.stream().anyMatch(Objects::isNull)
        || new HashSet<>(dates).size() != dates.size())
      throw new IllegalArgumentException("Distinct examination days are required");
    return dates.stream()
        .map(d -> new HealthExaminationBatchDay(UuidV7Generator.generate(), d))
        .toList();
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
