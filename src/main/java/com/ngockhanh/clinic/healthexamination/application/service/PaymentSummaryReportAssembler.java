package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentAggregates;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the payment summary of an agreement from the stored aggregates. It is the single source of
 * the figures: the JSON report, the screen and the Word document all show what this class returns.
 *
 * <p>Every offered agreement service appears, in display order. A service performed at several
 * historical prices appears once per price, because the amount uses the price snapshot of each
 * performed row, never the current negotiated price. A service nobody performed appears with zero
 * Participants at its current negotiated price so the report can be reconciled line by line; a
 * retired line appears only when something was performed with it.
 */
@Component
public class PaymentSummaryReportAssembler {
  private static final int MONEY_SCALE = 2;

  /**
   * Assembles the report.
   *
   * @param catalog catalog services keyed by id; a service missing from it gets a {@code null} code
   *     and name
   * @param rangeFrom first session date the aggregates were limited to, or null
   * @param rangeTo last session date the aggregates were limited to, or null
   * @param generatedAt time stamped on the report
   */
  public PaymentSummaryReportResponse assemble(
      HealthExaminationAgreement agreement,
      PaymentAggregates aggregates,
      Map<UUID, ServiceCatalogQuery.Service> catalog,
      LocalDate rangeFrom,
      LocalDate rangeTo,
      Instant generatedAt) {
    Map<UUID, List<PaymentAggregates.PerformedService>> performed = new HashMap<>();
    for (var line : aggregates.lines())
      performed.computeIfAbsent(line.agreementServiceId(), key -> new ArrayList<>()).add(line);

    List<PaymentSummaryReportResponse.Item> items = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO.setScale(MONEY_SCALE);
    for (HealthExaminationAgreementService service : agreement.services()) {
      var known = catalog.get(service.serviceId().value());
      List<PaymentAggregates.PerformedService> lines = performed.get(service.id().value());
      if (lines == null || lines.isEmpty()) {
        if (!service.active()) continue;
        BigDecimal price = service.negotiatedPrice().amount().setScale(MONEY_SCALE);
        items.add(item(service, known, price, 0));
        continue;
      }
      lines.sort(Comparator.comparing(PaymentAggregates.PerformedService::unitPrice));
      for (var line : lines) {
        var item =
            item(service, known, line.unitPrice().setScale(MONEY_SCALE), line.examinedCount());
        items.add(item);
        total = total.add(item.amount());
      }
    }
    var counts = aggregates.counts();
    return PaymentSummaryReportResponse.builder()
        .agreementId(agreement.id().value())
        .agreementCode(agreement.code())
        .agreementName(agreement.name())
        .agreementStatus(agreement.status().name())
        .validFrom(agreement.validFrom())
        .validTo(agreement.validTo())
        .rangeFrom(rangeFrom)
        .rangeTo(rangeTo)
        .provisional(agreement.status() != AgreementStatus.ENDED)
        .registeredCount(counts.registered())
        .attendedCount(counts.attended())
        .reconciledCount(counts.reconciled())
        .sessionCount(counts.sessions())
        .visitCount(counts.visits())
        .items(items)
        .totalAmount(total)
        .generatedAt(generatedAt)
        .build();
  }

  private static PaymentSummaryReportResponse.Item item(
      HealthExaminationAgreementService service,
      ServiceCatalogQuery.Service known,
      BigDecimal unitPrice,
      long examinedCount) {
    return PaymentSummaryReportResponse.Item.builder()
        .agreementServiceId(service.id().value())
        .serviceCode(known == null ? null : known.code())
        .serviceName(known == null ? null : known.name())
        .displayOrder(service.displayOrder())
        .unitPrice(unitPrice)
        .examinedCount(examinedCount)
        .amount(unitPrice.multiply(BigDecimal.valueOf(examinedCount)).setScale(MONEY_SCALE))
        .build();
  }
}
