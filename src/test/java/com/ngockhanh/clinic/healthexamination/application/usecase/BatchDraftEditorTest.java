package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand.ServicePrice;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class BatchDraftEditorTest {
  @Test
  void createsBatchDaysAndSnapshotsCatalogPrices() {
    var catalog = mock(ServiceCatalogQuery.class);
    var editor = new BatchDraftEditor(catalog);
    var service = UUID.randomUUID();
    var batch = new AggregateId(UUID.randomUUID());
    var firstDate = LocalDate.of(2026, 10, 4);
    var secondDate = LocalDate.of(2026, 10, 5);
    var days = editor.days(List.of(firstDate, secondDate));
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(
            List.of(
                new ServiceCatalogQuery.Service(
                    service, "S1", "Exam", true, new BigDecimal("200"))));
    var services =
        editor.services(
            batch,
            List.of(
                ServicePrice.builder()
                    .serviceId(service)
                    .negotiatedPrice(new BigDecimal("100"))
                    .build()));

    assertThat(days)
        .extracting(HealthExaminationBatchDay::examinationDate)
        .containsExactly(firstDate, secondDate);
    assertThat(days).extracting(HealthExaminationBatchDay::id).doesNotHaveDuplicates();
    assertThat(services).hasSize(1);
    assertThat(services.getFirst().batchId()).isEqualTo(batch);
    assertThat(services.getFirst().referencePriceSnapshot().amount()).isEqualByComparingTo("200");
    assertThat(services.getFirst().negotiatedPrice().amount()).isEqualByComparingTo("100");
    assertThat(services.getFirst().rowVersion()).isZero();
  }

  @Test
  void rejectsDuplicateDaysAndUnavailableOrDuplicateServices() {
    var catalog = mock(ServiceCatalogQuery.class);
    var editor = new BatchDraftEditor(catalog);
    var id = UUID.randomUUID();
    when(catalog.findByIds(Set.of(id)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(id, "S1", "Exam", false, BigDecimal.TEN)));
    var item = ServicePrice.builder().serviceId(id).negotiatedPrice(BigDecimal.ONE).build();
    var batch = new AggregateId(UUID.randomUUID());
    var date = LocalDate.of(2026, 10, 4);
    assertThatThrownBy(() -> editor.days(List.of(date, date)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> editor.days(List.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> editor.services(batch, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> editor.services(batch, List.of(item)))
        .isInstanceOf(BusinessRuleException.class);
    assertThatThrownBy(() -> editor.services(batch, List.of(item, item)))
        .isInstanceOf(BusinessRuleException.class);
    when(catalog.findByIds(Set.of(id)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(id, "S1", "Exam", true, BigDecimal.TEN)));
    assertThatThrownBy(
            () ->
                editor.services(
                    batch,
                    List.of(
                        ServicePrice.builder()
                            .serviceId(id)
                            .negotiatedPrice(new BigDecimal("-1.00"))
                            .build())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
