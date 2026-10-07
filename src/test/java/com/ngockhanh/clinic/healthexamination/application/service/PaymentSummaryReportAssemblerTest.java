package com.ngockhanh.clinic.healthexamination.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.BatchFixtures;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentAggregates;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentSummaryReportAssemblerTest {
  private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
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
    var batch = BatchFixtures.draftBatch(organizationId, batchId, 0, catalogA, catalogB);
    var serviceA = batch.services().get(0).id().value();
    var aggregates =
        new PaymentAggregates(
            new PaymentAggregates.ParticipantCounts(5, 3, 2),
            List.of(
                new PaymentAggregates.PerformedService(serviceA, new BigDecimal("150"), 1),
                new PaymentAggregates.PerformedService(serviceA, new BigDecimal("100"), 2)));

    var report = assembler.assemble(batch, aggregates, catalog(), NOW);

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
    assertThat(report.generatedAt()).isEqualTo(NOW);
  }

  @Test
  void isProvisionalUntilTheBatchIsFinalized() {
    var empty = new PaymentAggregates(new PaymentAggregates.ParticipantCounts(0, 0, 0), List.of());
    for (BatchStatus status : BatchStatus.values()) {
      var batch = BatchFixtures.batch(organizationId, batchId, status, 0, null, catalogA);
      var report = assembler.assemble(batch, empty, catalog(), NOW);
      boolean closed = status == BatchStatus.FINALIZED || status == BatchStatus.CLOSED;
      assertThat(report.provisional()).as(status.name()).isEqualTo(!closed);
      assertThat(report.totalAmount()).isEqualByComparingTo("0");
    }
  }
}
