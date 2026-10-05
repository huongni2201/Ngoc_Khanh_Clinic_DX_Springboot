package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListHealthExaminationBatchUseCaseTest {
  @Test
  void returnsScopedPageUsingAllowedSortAndEscapedSearch() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    var organizations = mock(OrganizationRepository.class);
    var batches = mock(HealthExaminationBatchRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.of(organization(id)));
    when(batches.count(organizationId, "%clinic\\%\\_%")).thenReturn(12L);
    when(batches.findPage(organizationId, 2, 2, "%clinic\\%\\_%", "startDate", "DESC"))
        .thenReturn(
            List.of(
                summary("B2", LocalDate.of(2026, 10, 4)),
                summary("B1", LocalDate.of(2026, 10, 5))));

    var page =
        new ListHealthExaminationBatchUseCase(organizations, batches)
            .execute(
                organizationId,
                new HealthExaminationBatchListQuery(2, 2, "Clinic%_", "startDate", "DESC"));

    assertThat(page.items()).extracting(item -> item.batchCode()).containsExactly("B2", "B1");
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalElements()).isEqualTo(12);
    assertThat(page.totalPages()).isEqualTo(6);
    verify(batches).count(organizationId, "%clinic\\%\\_%");
    verify(batches).findPage(organizationId, 2, 2, "%clinic\\%\\_%", "startDate", "DESC");
  }

  @Test
  void returnsEmptyPageWhenOrganizationHasNoBatches() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    var organizations = mock(OrganizationRepository.class);
    var batches = mock(HealthExaminationBatchRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.of(organization(id)));
    when(batches.count(organizationId, null)).thenReturn(0L);
    when(batches.findPage(organizationId, 0, 10, null, "id", "ASC")).thenReturn(List.of());

    var page =
        new ListHealthExaminationBatchUseCase(organizations, batches)
            .execute(organizationId, new HealthExaminationBatchListQuery(1, 10, null, null, null));

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  void missingOrganizationReturnsNotFoundBeforeBatchQueries() {
    UUID organizationId = UUID.randomUUID();
    var organizations = mock(OrganizationRepository.class);
    var batches = mock(HealthExaminationBatchRepository.class);
    when(organizations.findById(AggregateId.of(organizationId))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new ListHealthExaminationBatchUseCase(organizations, batches)
                    .execute(
                        organizationId,
                        new HealthExaminationBatchListQuery(1, 10, null, null, null)))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(batches);
  }

  @Test
  void rejectsInvalidPaginationBeforeQueryingBatches() {
    UUID organizationId = UUID.randomUUID();
    var organizations = mock(OrganizationRepository.class);
    var batches = mock(HealthExaminationBatchRepository.class);
    when(organizations.findById(AggregateId.of(organizationId)))
        .thenReturn(Optional.of(organization(AggregateId.of(organizationId))));

    assertThatThrownBy(
            () ->
                new ListHealthExaminationBatchUseCase(organizations, batches)
                    .execute(
                        organizationId,
                        new HealthExaminationBatchListQuery(0, 10, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(batches);
  }

  @Test
  void rejectsUnsupportedSortKeyBeforeQueryingBatches() {
    UUID organizationId = UUID.randomUUID();
    var organizations = mock(OrganizationRepository.class);
    var batches = mock(HealthExaminationBatchRepository.class);
    when(organizations.findById(AggregateId.of(organizationId)))
        .thenReturn(Optional.of(organization(AggregateId.of(organizationId))));

    assertThatThrownBy(
            () ->
                new ListHealthExaminationBatchUseCase(organizations, batches)
                    .execute(
                        organizationId,
                        new HealthExaminationBatchListQuery(
                            1, 10, null, "id; DROP TABLE services", "ASC")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(batches);
  }

  private static Organization organization(AggregateId id) {
    return Organization.create(
        id,
        "ORG-01",
        "Clinic Partner",
        "COMPANY",
        null,
        "0901",
        "office@example.test",
        "Address",
        "Contact Person",
        null,
        "0902",
        "contact@example.test");
  }

  private static HealthExaminationBatchRepository.BatchSummary summary(
      String code, LocalDate startDate) {
    return new HealthExaminationBatchRepository.BatchSummary(
        UUID.randomUUID(),
        code,
        "Campaign " + code,
        startDate,
        startDate,
        "DRAFT",
        Instant.parse("2026-10-04T00:00:00Z"),
        Instant.parse("2026-10-04T00:00:00Z"));
  }
}
