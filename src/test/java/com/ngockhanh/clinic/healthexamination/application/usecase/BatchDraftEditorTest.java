package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.catalog.application.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class BatchDraftEditorTest {
  @Test
  void rejectsDuplicateServicesBeforeLookup() {
    var catalog = mock(ServiceCatalogQuery.class);
    var ids = mock(IdGenerator.class);
    var editor = new BatchDraftEditor(catalog, ids);
    UUID service = UUID.randomUUID();
    var item = new BatchConfigurationCommand.ServicePrice(service, new BigDecimal("10.00"));
    assertThatThrownBy(
            () ->
                editor.services(new AggregateId(UUID.randomUUID()), List.of(item, item), List.of()))
        .isInstanceOf(RuntimeException.class);
    verifyNoInteractions(catalog, ids);
  }

  @Test
  void validatesEligibilityAndUsesOnlyEnteredPrice() {
    var catalog = mock(ServiceCatalogQuery.class);
    var ids = mock(IdGenerator.class);
    UUID service = UUID.randomUUID();
    UUID row = UUID.randomUUID();
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(List.of(new ServiceCatalogQuery.Service(service, "S1", "Exam", true, true)));
    when(ids.next()).thenReturn(row);
    var result =
        new BatchDraftEditor(catalog, ids)
            .services(
                new AggregateId(UUID.randomUUID()),
                List.of(
                    new BatchConfigurationCommand.ServicePrice(service, new BigDecimal("123.00"))),
                List.of());
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().negotiatedPrice().amount()).isEqualByComparingTo("123.00");
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(List.of(new ServiceCatalogQuery.Service(service, "S1", "Exam", false, true)));
    assertThatThrownBy(
            () ->
                new BatchDraftEditor(catalog, ids)
                    .services(
                        new AggregateId(UUID.randomUUID()),
                        List.of(
                            new BatchConfigurationCommand.ServicePrice(service, BigDecimal.TEN)),
                        List.of()))
        .isInstanceOf(RuntimeException.class);
  }
}
