package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDay;
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
      AggregateId batchId,
      List<BatchConfigurationCommand.ServicePrice> items,
      List<HealthExaminationBatchService> existing) {
    if (items == null || items.isEmpty())
      throw new IllegalArgumentException("Batch services are required");
    Set<UUID> requested = new HashSet<>();
    for (var item : items)
      if (item == null || item.serviceId() == null || !requested.add(item.serviceId()))
        throw new BusinessRuleException("Duplicate or missing service");
    Map<UUID, ServiceCatalogQuery.Service> found = new HashMap<>();
    catalog.findByIds(requested).forEach(s -> found.put(s.id(), s));
    Map<UUID, HealthExaminationBatchService> previous = new HashMap<>();
    existing.forEach(s -> previous.put(s.serviceId().value(), s));
    List<HealthExaminationBatchService> result = new ArrayList<>();
    for (var item : items) {
      var old = previous.get(item.serviceId());
      var service = found.get(item.serviceId());
      if (old == null && (service == null || !service.active()))
        throw new BusinessRuleException("Service is inactive or unavailable");
      result.add(
          new HealthExaminationBatchService(
              old == null ? new AggregateId(UuidV7Generator.generate()) : old.id(),
              new AggregateId(item.serviceId()),
              batchId,
              old == null ? new Money(service.unitPrice(), "VND") : old.referencePriceSnapshot(),
              new Money(item.negotiatedPrice(), "VND"),
              result.size() + 1,
              old == null || old.active(),
              old == null ? 0 : old.rowVersion()));
    }
    return List.copyOf(result);
  }

  public List<BatchDay> days(List<LocalDate> dates, List<BatchDay> existing) {
    if (dates == null
        || dates.isEmpty()
        || dates.stream().anyMatch(Objects::isNull)
        || new HashSet<>(dates).size() != dates.size())
      throw new IllegalArgumentException("Distinct examination days are required");
    Map<LocalDate, BatchDay> previous = new HashMap<>();
    existing.forEach(d -> previous.put(d.examinationDate(), d));
    return dates.stream()
        .map(
            d ->
                previous.containsKey(d)
                    ? previous.get(d)
                    : new BatchDay(UuidV7Generator.generate(), d))
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
