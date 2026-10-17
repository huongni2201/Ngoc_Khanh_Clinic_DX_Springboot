package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationAgreementListQuery;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListHealthExaminationAgreementUseCaseTest {
  @Test
  void returnsScopedPageUsingAllowedSortAndEscapedSearch() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    var organizations = mock(OrganizationRepository.class);
    var agreements = mock(HealthExaminationAgreementRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.of(organization(id)));
    when(agreements.count(organizationId, "%clinic\\%\\_%")).thenReturn(12L);
    when(agreements.findPage(organizationId, 2, 2, "%clinic\\%\\_%", "validFrom", "DESC"))
        .thenReturn(
            List.of(
                summary("B2", LocalDate.of(2026, 10, 4)),
                summary("B1", LocalDate.of(2026, 10, 5))));

    var page =
        new ListHealthExaminationAgreementUseCase(organizations, agreements)
            .execute(
                organizationId,
                HealthExaminationAgreementListQuery.builder()
                    .page(2)
                    .size(2)
                    .searchKey("Clinic%_")
                    .sortKey("validFrom")
                    .sortBy("DESC")
                    .build());

    assertThat(page.items()).extracting(item -> item.agreementCode()).containsExactly("B2", "B1");
    assertThat(page.items()).extracting(item -> item.rowVersion()).containsExactly(7L, 7L);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalElements()).isEqualTo(12);
    assertThat(page.totalPages()).isEqualTo(6);
    verify(agreements).count(organizationId, "%clinic\\%\\_%");
    verify(agreements).findPage(organizationId, 2, 2, "%clinic\\%\\_%", "validFrom", "DESC");
  }

  @Test
  void returnsEmptyPageWhenOrganizationHasNoAgreements() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    var organizations = mock(OrganizationRepository.class);
    var agreements = mock(HealthExaminationAgreementRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.of(organization(id)));
    when(agreements.count(organizationId, null)).thenReturn(0L);
    when(agreements.findPage(organizationId, 0, 10, null, "id", "ASC")).thenReturn(List.of());

    var page =
        new ListHealthExaminationAgreementUseCase(organizations, agreements)
            .execute(
                organizationId,
                HealthExaminationAgreementListQuery.builder()
                    .page(1)
                    .size(10)
                    .searchKey(null)
                    .sortKey(null)
                    .sortBy(null)
                    .build());

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  void missingOrganizationReturnsNotFoundBeforeAgreementQueries() {
    UUID organizationId = UUID.randomUUID();
    var organizations = mock(OrganizationRepository.class);
    var agreements = mock(HealthExaminationAgreementRepository.class);
    when(organizations.findById(AggregateId.of(organizationId))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new ListHealthExaminationAgreementUseCase(organizations, agreements)
                    .execute(
                        organizationId,
                        HealthExaminationAgreementListQuery.builder()
                            .page(1)
                            .size(10)
                            .searchKey(null)
                            .sortKey(null)
                            .sortBy(null)
                            .build()))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(agreements);
  }

  @Test
  void rejectsInvalidPaginationBeforeQueryingAgreements() {
    UUID organizationId = UUID.randomUUID();
    var organizations = mock(OrganizationRepository.class);
    var agreements = mock(HealthExaminationAgreementRepository.class);
    when(organizations.findById(AggregateId.of(organizationId)))
        .thenReturn(Optional.of(organization(AggregateId.of(organizationId))));

    assertThatThrownBy(
            () ->
                new ListHealthExaminationAgreementUseCase(organizations, agreements)
                    .execute(
                        organizationId,
                        HealthExaminationAgreementListQuery.builder()
                            .page(0)
                            .size(10)
                            .searchKey(null)
                            .sortKey(null)
                            .sortBy(null)
                            .build()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(agreements);
  }

  @Test
  void rejectsUnsupportedSortKeyBeforeQueryingAgreements() {
    UUID organizationId = UUID.randomUUID();
    var organizations = mock(OrganizationRepository.class);
    var agreements = mock(HealthExaminationAgreementRepository.class);
    when(organizations.findById(AggregateId.of(organizationId)))
        .thenReturn(Optional.of(organization(AggregateId.of(organizationId))));

    assertThatThrownBy(
            () ->
                new ListHealthExaminationAgreementUseCase(organizations, agreements)
                    .execute(
                        organizationId,
                        HealthExaminationAgreementListQuery.builder()
                            .page(1)
                            .size(10)
                            .searchKey(null)
                            .sortKey("id; DROP TABLE services")
                            .sortBy("ASC")
                            .build()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(agreements);
  }

  private static HealthExaminationAgreementListQuery query(
      Integer page, Integer size, String searchKey, String sortKey, String sortBy) {
    return HealthExaminationAgreementListQuery.builder()
        .page(page)
        .size(size)
        .searchKey(searchKey)
        .sortKey(sortKey)
        .sortBy(sortBy)
        .build();
  }

  private final UUID listedOrganization = UUID.randomUUID();
  private final OrganizationRepository listOrganizations = mock(OrganizationRepository.class);
  private final HealthExaminationAgreementRepository listAgreements =
      mock(HealthExaminationAgreementRepository.class);
  private final ListHealthExaminationAgreementUseCase listUseCase =
      new ListHealthExaminationAgreementUseCase(listOrganizations, listAgreements);

  private void organizationExists() {
    when(listOrganizations.findById(AggregateId.of(listedOrganization)))
        .thenReturn(Optional.of(organization(AggregateId.of(listedOrganization))));
  }

  @Test
  void aPagePastTheEndIsEmptyButKeepsTheTotals() {
    organizationExists();
    when(listAgreements.count(listedOrganization, null)).thenReturn(5L);

    var page = listUseCase.execute(listedOrganization, query(4, 2, null, null, null));

    assertThat(page.items()).isEmpty();
    assertThat(page.page()).isEqualTo(4);
    assertThat(page.totalElements()).isEqualTo(5);
    assertThat(page.totalPages()).isEqualTo(3);
    verify(listAgreements, never()).findPage(any(), anyLong(), anyInt(), any(), any(), any());
  }

  @Test
  void blankSearchMeansNoFilterAndSortDirectionIsNormalized() {
    organizationExists();
    when(listAgreements.count(listedOrganization, null)).thenReturn(1L);
    when(listAgreements.findPage(listedOrganization, 0, 10, null, "agreementCode", "DESC"))
        .thenReturn(List.of(summary("B1", LocalDate.of(2026, 10, 4))));

    var page =
        listUseCase.execute(listedOrganization, query(1, 10, "   ", "agreementCode", " desc "));

    assertThat(page.items()).hasSize(1);
    verify(listAgreements).findPage(listedOrganization, 0, 10, null, "agreementCode", "DESC");
  }

  @Test
  void nullPagingFallsBackToTheDefaults() {
    organizationExists();
    when(listAgreements.count(listedOrganization, null)).thenReturn(0L);

    var page = listUseCase.execute(listedOrganization, query(null, null, null, null, null));

    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(10);
  }

  @Test
  void anInactiveOrganizationStillListsItsAgreements() {
    when(listOrganizations.findById(AggregateId.of(listedOrganization)))
        .thenReturn(
            Optional.of(
                com.ngockhanh.clinic.healthexamination.AgreementFixtures.inactiveOrganization(
                    listedOrganization)));
    when(listAgreements.count(listedOrganization, null)).thenReturn(0L);

    assertThat(listUseCase.execute(listedOrganization, query(1, 10, null, null, null)).items())
        .isEmpty();
  }

  @Test
  void rejectsOutOfRangeSizeLongSearchBadDirectionAndNullArguments() {
    organizationExists();
    assertThatThrownBy(() -> listUseCase.execute(listedOrganization, query(1, 0, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> listUseCase.execute(listedOrganization, query(1, 100000, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                listUseCase.execute(listedOrganization, query(1, 10, "x".repeat(101), null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> listUseCase.execute(listedOrganization, query(1, 10, null, "id", "SIDEWAYS")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> listUseCase.execute(null, query(1, 10, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> listUseCase.execute(listedOrganization, null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(listAgreements);
  }

  private static Organization organization(AggregateId id) {
    return Organization.create(
        id,
        "Clinic Partner",
        null,
        "0901",
        "office@example.test",
        "Address",
        "Contact Person",
        "0902",
        "contact@example.test");
  }

  private static HealthExaminationAgreementRepository.AgreementSummary summary(
      String code, LocalDate startDate) {
    return new HealthExaminationAgreementRepository.AgreementSummary(
        UUID.randomUUID(),
        code,
        "Campaign " + code,
        startDate,
        startDate,
        AgreementStatus.DRAFT,
        Instant.parse("2026-10-04T00:00:00Z"),
        Instant.parse("2026-10-04T00:00:00Z"),
        7L);
  }
}
