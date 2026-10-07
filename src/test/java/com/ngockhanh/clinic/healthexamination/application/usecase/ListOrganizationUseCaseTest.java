package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.command.ListOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListOrganizationUseCaseTest {
  private final OrganizationRepository repository = mock(OrganizationRepository.class);
  private final ListOrganizationUseCase useCase = new ListOrganizationUseCase(repository);

  private Organization organization(String taxCode) {
    return Organization.create(
        new AggregateId(UUID.randomUUID()),
        "School " + taxCode,
        taxCode,
        "0901",
        "o@example.test",
        "Address",
        "Contact",
        "0902",
        "c@example.test");
  }

  @Test
  void appliesDefaultsAndOnlyAsksForActiveOrganizations() {
    when(repository.search(1, 10, null, "id", "ASC", OrganizationStatus.ACTIVE))
        .thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));

    var result = useCase.execute(ListOrganizationCommand.builder().build());

    verify(repository).search(1, 10, null, "id", "ASC", OrganizationStatus.ACTIVE);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(10);
  }

  @Test
  void normalizesSearchKeyAndSortDirection() {
    when(repository.search(3, 25, "clinic", "name", "DESC", OrganizationStatus.ACTIVE))
        .thenReturn(new PageResponse<>(List.of(), 3, 25, 0, 0));

    useCase.execute(
        ListOrganizationCommand.builder()
            .page(3)
            .size(25)
            .searchKey("  clinic  ")
            .sortKey("name")
            .sortBy("desc")
            .build());

    verify(repository).search(3, 25, "clinic", "name", "DESC", OrganizationStatus.ACTIVE);
  }

  @Test
  void blankSearchKeyMeansNoSearch() {
    when(repository.search(1, 10, null, "id", "ASC", OrganizationStatus.ACTIVE))
        .thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));

    useCase.execute(ListOrganizationCommand.builder().searchKey("   ").build());

    verify(repository).search(1, 10, null, "id", "ASC", OrganizationStatus.ACTIVE);
  }

  @Test
  void mapsItemsAndPreservesPaginationMetadata() {
    var first = organization("A1");
    var second = organization("A2");
    when(repository.search(2, 10, null, "id", "ASC", OrganizationStatus.ACTIVE))
        .thenReturn(new PageResponse<>(List.of(first, second), 2, 10, 25, 3));

    var result = useCase.execute(ListOrganizationCommand.builder().page(2).size(10).build());

    assertThat(result.items()).extracting("taxCode").containsExactly("A1", "A2");
    assertThat(result.page()).isEqualTo(2);
    assertThat(result.size()).isEqualTo(10);
    assertThat(result.totalElements()).isEqualTo(25);
    assertThat(result.totalPages()).isEqualTo(3);
  }

  @Test
  void emptyDatasetHasZeroTotalPages() {
    when(repository.search(1, 10, null, "id", "ASC", OrganizationStatus.ACTIVE))
        .thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));

    var result = useCase.execute(ListOrganizationCommand.builder().build());

    assertThat(result.items()).isEmpty();
    assertThat(result.totalElements()).isZero();
    assertThat(result.totalPages()).isZero();
  }

  @Test
  void pageBeyondTheEndKeepsTotalsOfTheFilteredResult() {
    when(repository.search(9, 10, null, "id", "ASC", OrganizationStatus.ACTIVE))
        .thenReturn(new PageResponse<>(List.of(), 9, 10, 11, 2));

    var result = useCase.execute(ListOrganizationCommand.builder().page(9).size(10).build());

    assertThat(result.items()).isEmpty();
    assertThat(result.totalElements()).isEqualTo(11);
    assertThat(result.totalPages()).isEqualTo(2);
  }

  @Test
  void nullCommandIsRejected() {
    assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void rejectsPageBelowOne(int page) {
    assertThatThrownBy(() -> useCase.execute(ListOrganizationCommand.builder().page(page).build()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -5, 101})
  void rejectsSizeOutsideOneToOneHundred(int size) {
    assertThatThrownBy(() -> useCase.execute(ListOrganizationCommand.builder().size(size).build()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"status", "tax_code; DROP TABLE organizations", "code", "name desc"})
  void rejectsSortKeyOutsideAllowlist(String sortKey) {
    assertThatThrownBy(
            () -> useCase.execute(ListOrganizationCommand.builder().sortKey(sortKey).build()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"UP", "ASC; DROP", "descending"})
  void rejectsUnknownSortDirection(String sortBy) {
    assertThatThrownBy(
            () -> useCase.execute(ListOrganizationCommand.builder().sortBy(sortBy).build()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void rejectsOverlongSearchKey() {
    var tooLong = "x".repeat(ListOrganizationUseCase.MAX_SEARCH_KEY_LENGTH + 1);

    assertThatThrownBy(
            () -> useCase.execute(ListOrganizationCommand.builder().searchKey(tooLong).build()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void acceptsMaximumPageNumber() {
    when(repository.search(Integer.MAX_VALUE, 100, null, "id", "ASC", OrganizationStatus.ACTIVE))
        .thenReturn(new PageResponse<>(List.of(), Integer.MAX_VALUE, 100, 0, 0));

    var result =
        useCase.execute(
            ListOrganizationCommand.builder().page(Integer.MAX_VALUE).size(100).build());

    verify(repository).search(Integer.MAX_VALUE, 100, null, "id", "ASC", OrganizationStatus.ACTIVE);
    assertThat(result.page()).isEqualTo(Integer.MAX_VALUE);
  }
}
