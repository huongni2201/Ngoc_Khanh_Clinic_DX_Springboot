package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand.ServicePrice;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDay;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class BatchDraftEditorTest {
  @Test
  void snapshotsCatalogPriceOnceAndPreservesItDuringLaterEdits() {
    var catalog = mock(ServiceCatalogQuery.class);
    var editor = new BatchDraftEditor(catalog);
    var service = UUID.randomUUID();
    var batch = new AggregateId(UUID.randomUUID());
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(
            List.of(
                new ServiceCatalogQuery.Service(
                    service, "S1", "Exam", true, new BigDecimal("200"))));
    var first =
        editor.services(
            batch, List.of(new ServicePrice(service, new BigDecimal("100"))), List.of());
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(
            List.of(
                new ServiceCatalogQuery.Service(
                    service, "S1", "Renamed", false, new BigDecimal("300"))));
    var revised =
        editor
            .services(batch, List.of(new ServicePrice(service, new BigDecimal("120"))), first)
            .getFirst();
    assertThat(revised.id()).isEqualTo(first.getFirst().id());
    assertThat(revised.referencePriceSnapshot().amount()).isEqualByComparingTo("200");
    assertThat(revised.negotiatedPrice().amount()).isEqualByComparingTo("120");
  }

  @Test
  void rejectsInactiveNewServicesDuplicateSelectionAndPreservesRetainedDayIds() {
    var catalog = mock(ServiceCatalogQuery.class);
    var editor = new BatchDraftEditor(catalog);
    var id = UUID.randomUUID();
    when(catalog.findByIds(Set.of(id)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(id, "S1", "Exam", false, BigDecimal.TEN)));
    var item = new ServicePrice(id, BigDecimal.ONE);
    var batch = new AggregateId(UUID.randomUUID());
    assertThatThrownBy(() -> editor.services(batch, List.of(item), List.of()))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> editor.services(batch, List.of(item, item), List.of()))
        .isInstanceOf(RuntimeException.class);
    var date = LocalDate.of(2026, 10, 4);
    var day = new BatchDay(UUID.randomUUID(), date);
    assertThat(editor.days(List.of(date), List.of(day))).containsExactly(day);
  }
}
