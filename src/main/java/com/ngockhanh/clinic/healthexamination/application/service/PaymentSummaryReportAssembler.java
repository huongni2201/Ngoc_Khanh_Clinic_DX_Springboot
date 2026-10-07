package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentAggregates;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the payment summary of a batch from the stored aggregates. It is the single source of the
 * figures: the JSON report, the screen and the Word document all show what this class returns.
 *
 * <p>Every batch service appears, in display order. A service performed at several historical
 * prices appears once per price, because the amount uses the price snapshot of each performed row,
 * never the current negotiated price. A service nobody performed appears with zero Participants at
 * its current negotiated price so the report can be reconciled line by line.
 */
@Component
public class PaymentSummaryReportAssembler {
  private static final int MONEY_SCALE = 2;

  /**
   * Assembles the report.
   *
   * @param catalog catalog services keyed by id; a service missing from it gets a {@code null} code
   *     and name
   * @param generatedAt time stamped on the report
   */
  public PaymentSummaryReportResponse assemble(
      HealthExaminationBatch batch,
      PaymentAggregates aggregates,
      Map<UUID, ServiceCatalogQuery.Service> catalog,
      Instant generatedAt) {
    Map<UUID, List<PaymentAggregates.PerformedService>> performed = new HashMap<>();
    for (var line : aggregates.lines())
      performed.computeIfAbsent(line.batchServiceId(), key -> new ArrayList<>()).add(line);

    List<PaymentSummaryReportResponse.Item> items = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO.setScale(MONEY_SCALE);
    for (HealthExaminationBatchService service : batch.services()) {
      var known = catalog.get(service.serviceId().value());
      List<PaymentAggregates.PerformedService> lines = performed.get(service.id().value());
      if (lines == null || lines.isEmpty()) {
        BigDecimal price = service.negotiatedPrice().amount().setScale(MONEY_SCALE);
        items.add(item(service, known, price, 0));
        continue;
      }
      lines.sort(Comparator.comparing(PaymentAggregates.PerformedService::unitPrice));
      for (var line : lines) {
        var item = item(service, known, line.unitPrice().setScale(MONEY_SCALE), line.examinedCount());
        items.add(item);
        total = total.add(item.amount());
      }
    }
    return PaymentSummaryReportResponse.builder()
        .batchId(batch.id().value())
        .batchCode(batch.code())
        .batchName(batch.name())
        .batchStatus(batch.status().name())
        .provisional(batch.status() != BatchStatus.FINALIZED && batch.status() != BatchStatus.CLOSED)
        .registeredCount(aggregates.counts().registered())
        .attendedCount(aggregates.counts().attended())
        .reconciledCount(aggregates.counts().reconciled())
        .items(items)
        .totalAmount(total)
        .generatedAt(generatedAt)
        .build();
  }

  private static PaymentSummaryReportResponse.Item item(
      HealthExaminationBatchService service,
      ServiceCatalogQuery.Service known,
      BigDecimal unitPrice,
      long examinedCount) {
    return PaymentSummaryReportResponse.Item.builder()
        .batchServiceId(service.id().value())
        .serviceCode(known == null ? null : known.code())
        .serviceName(known == null ? null : known.name())
        .displayOrder(service.displayOrder())
        .unitPrice(unitPrice)
        .examinedCount(examinedCount)
        .amount(unitPrice.multiply(BigDecimal.valueOf(examinedCount)).setScale(MONEY_SCALE))
        .build();
  }
}
