package com.ngockhanh.clinic.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogListQuery;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery.ServiceItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListServiceCatalogUseCaseTest {
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final ListServiceCatalogUseCase useCase = new ListServiceCatalogUseCase(catalog);

  private static ServiceItem item(String code) {
    return new ServiceItem(
        UUID.randomUUID(), code, "Service " + code, "LAB", new BigDecimal("90000.00"), true);
  }

  @Test
  void returnsTheRequestedPageUsingAllowedSortAndEscapedSearch() {
    when(catalog.countActive("%blood\\%\\_%")).thenReturn(12L);
    when(catalog.findActivePage("%blood\\%\\_%", 2, 2, "unitPrice", "DESC"))
        .thenReturn(List.of(item("S2"), item("S1")));

    var page =
        useCase.execute(
            ServiceCatalogListQuery.builder()
                .page(2)
                .size(2)
                .searchKey(" Blood%_ ")
                .sortKey("unitPrice")
                .sortBy("desc")
                .build());

    assertThat(page.items()).extracting(item -> item.code()).containsExactly("S2", "S1");
    assertThat(page.items().getFirst().unitPrice()).isEqualByComparingTo("90000");
    assertThat(page.items().getFirst().active()).isTrue();
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalElements()).isEqualTo(12);
    assertThat(page.totalPages()).isEqualTo(6);
  }

  @Test
  void usesDefaultsAndReturnsAnEmptyPageWithoutQueryingRowsWhenNothingMatches() {
    when(catalog.countActive(null)).thenReturn(0L);

    var page = useCase.execute(ServiceCatalogListQuery.builder().build());

    assertThat(page.items()).isEmpty();
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(10);
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
    verify(catalog, never()).findActivePage(any(), anyLong(), anyInt(), any(), any());
  }

  @Test
  void aPagePastTheEndKeepsTheTotalsAndHasNoItems() {
    when(catalog.countActive(null)).thenReturn(5L);

    var page =
        useCase.execute(ServiceCatalogListQuery.builder().page(3).size(10).build());

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(5);
    assertThat(page.totalPages()).isEqualTo(1);
    verify(catalog, never()).findActivePage(any(), anyLong(), anyInt(), any(), any());
  }

  @Test
  void rejectsInvalidInputBeforeReadingTheCatalog() {
    assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(IllegalArgumentException.class);
    for (var invalid :
        List.of(
            ServiceCatalogListQuery.builder().page(0).build(),
            ServiceCatalogListQuery.builder().size(0).build(),
            ServiceCatalogListQuery.builder().size(101).build(),
            ServiceCatalogListQuery.builder().searchKey("x".repeat(101)).build(),
            ServiceCatalogListQuery.builder().sortKey("id; DROP TABLE services").build(),
            ServiceCatalogListQuery.builder().sortBy("sideways").build())) {
      assertThatThrownBy(() -> useCase.execute(invalid))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verify(catalog, never()).countActive(any());
  }
}
