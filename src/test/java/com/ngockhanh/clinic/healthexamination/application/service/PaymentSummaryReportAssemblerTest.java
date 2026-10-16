package com.ngockhanh.clinic.healthexamination.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.AgreementFixtures;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentAggregates;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentSummaryReportAssemblerTest {
  private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID catalogA = UUID.randomUUID();
  private final UUID catalogB = UUID.randomUUID();
  private final PaymentSummaryReportAssembler assembler = new PaymentSummaryReportAssembler();

  private Map<UUID, ServiceCatalogQuery.Service> catalog() {
    return Map.of(
        catalogA,
        new ServiceCatalogQuery.Service(catalogA, "A", "Khám nội", true, new BigDecimal("200")));
  }

  @Test
  void listsEveryServiceInDisplayOrderAndSplitsHistoricalPrices() {
    var agreement =
        AgreementFixtures.draftAgreement(organizationId, agreementId, 0, catalogA, catalogB);
    var serviceA = agreement.services().get(0).id().value();
    var aggregates =
        new PaymentAggregates(
            new PaymentAggregates.ParticipantCounts(5, 3, 2, 2, 4),
            List.of(
                new PaymentAggregates.PerformedService(serviceA, new BigDecimal("150"), 1),
                new PaymentAggregates.PerformedService(serviceA, new BigDecimal("100"), 2)));

    var report = assembler.assemble(agreement, aggregates, catalog(), null, null, NOW);

    assertThat(report.items()).hasSize(3);
    assertThat(report.items().get(0).unitPrice()).isEqualByComparingTo("100");
    assertThat(report.items().get(0).examinedCount()).isEqualTo(2);
    assertThat(report.items().get(0).amount()).isEqualByComparingTo("200");
    assertThat(report.items().get(0).serviceName()).isEqualTo("Khám nội");
    assertThat(report.items().get(1).unitPrice()).isEqualByComparingTo("150");
    assertThat(report.items().get(1).amount()).isEqualByComparingTo("150");
    var unperformed = report.items().get(2);
    assertThat(unperformed.examinedCount()).isZero();
    assertThat(unperformed.unitPrice()).isEqualByComparingTo("100");
    assertThat(unperformed.amount()).isEqualByComparingTo("0");
    assertThat(unperformed.serviceName()).isNull();
    assertThat(report.totalAmount()).isEqualByComparingTo("350");
    assertThat(report.registeredCount()).isEqualTo(5);
    assertThat(report.attendedCount()).isEqualTo(3);
    assertThat(report.reconciledCount()).isEqualTo(2);
    assertThat(report.sessionCount()).isEqualTo(2);
    assertThat(report.visitCount()).isEqualTo(4);
    assertThat(report.generatedAt()).isEqualTo(NOW);
  }

  @Test
  void isProvisionalUntilTheAgreementEnds() {
    var empty =
        new PaymentAggregates(new PaymentAggregates.ParticipantCounts(0, 0, 0, 0, 0), List.of());
    for (AgreementStatus status : AgreementStatus.values()) {
      var agreement =
          AgreementFixtures.agreement(organizationId, agreementId, status, 0, null, catalogA);
      var report = assembler.assemble(agreement, empty, catalog(), null, null, NOW);
      boolean closed = status == AgreementStatus.ENDED;
      assertThat(report.provisional()).as(status.name()).isEqualTo(!closed);
      assertThat(report.totalAmount()).isEqualByComparingTo("0");
    }
  }

  @Test
  void aRetiredLineAppearsOnlyWhenSomethingWasPerformedWithItAndTheRangeIsEchoed() {
    var offered =
        AgreementFixtures.draftAgreement(organizationId, agreementId, 0, catalogA, catalogB);
    var agreement =
        HealthExaminationAgreement.restoreConfiguration(
            offered.id(),
            offered.organizationId(),
            offered.code(),
            offered.name(),
            offered.validFrom(),
            offered.validTo(),
            offered.services().stream()
                .map(
                    line ->
                        new HealthExaminationAgreementService(
                            line.id(),
                            line.serviceId(),
                            line.agreementId(),
                            line.referencePriceSnapshot(),
                            line.negotiatedPrice(),
                            line.displayOrder(),
                            false,
                            line.rowVersion()))
                .toList(),
            offered.status(),
            offered.rowVersion(),
            null);
    var retiredUnused = agreement.services().get(0);
    var retiredUsed = agreement.services().get(1);
    var aggregates =
        new PaymentAggregates(
            new PaymentAggregates.ParticipantCounts(1, 1, 1, 1, 1),
            List.of(
                new PaymentAggregates.PerformedService(
                    retiredUsed.id().value(), new BigDecimal("80"), 1)));
    var from = java.time.LocalDate.of(2026, 10, 4);
    var to = java.time.LocalDate.of(2026, 10, 5);

    var report = assembler.assemble(agreement, aggregates, catalog(), from, to, NOW);

    assertThat(report.items()).hasSize(1);
    assertThat(report.items().get(0).unitPrice()).isEqualByComparingTo("80");
    assertThat(report.totalAmount()).isEqualByComparingTo("80");
    assertThat(report.rangeFrom()).isEqualTo(from);
    assertThat(report.rangeTo()).isEqualTo(to);
    assertThat(report.validFrom()).isEqualTo(AgreementFixtures.FIRST_DAY);
    assertThat(report.validTo()).isEqualTo(AgreementFixtures.SECOND_DAY);
  }
}
