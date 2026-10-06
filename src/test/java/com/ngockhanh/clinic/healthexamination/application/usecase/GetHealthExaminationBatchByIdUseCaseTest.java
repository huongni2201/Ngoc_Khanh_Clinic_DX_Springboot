package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.service.BatchDetailResponseMapper;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetHealthExaminationBatchByIdUseCaseTest {
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final GetHealthExaminationBatchByIdUseCase useCase =
      new GetHealthExaminationBatchByIdUseCase(
          organizations, batches, new BatchDetailResponseMapper(catalog));
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();

  @Test
  void returnsTheBatchScopedByOrganizationAndBatchWithoutLocking() {
    UUID service = UUID.randomUUID();
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, false))
        .thenReturn(Optional.of(details(draftBatch(organizationId, batchId, 5, service))));

    var response = useCase.execute(organizationId, batchId);

    assertThat(response.id()).isEqualTo(batchId);
    assertThat(response.organizationId()).isEqualTo(organizationId);
    assertThat(response.rowVersion()).isEqualTo(5);
    assertThat(response.startDate()).isEqualTo(FIRST_DAY);
    assertThat(response.endDate()).isEqualTo(SECOND_DAY);
    assertThat(response.days()).hasSize(2);
    assertThat(response.services()).hasSize(1);
    assertThat(response.services().getFirst().serviceId()).isEqualTo(service);
    verify(batches).findDetails(organizationId, batchId, false);
  }

  @Test
  void addsTheCatalogCodeAndNameToEachServiceAndLeavesUnknownServicesUnnamed() {
    UUID known = UUID.randomUUID();
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, false))
        .thenReturn(Optional.of(details(draftBatch(organizationId, batchId, 1, known))));
    when(catalog.findByIds(java.util.Set.of(known)))
        .thenReturn(
            java.util.List.of(
                new ServiceCatalogQuery.Service(
                    known, "S1", "Khám tổng quát", false, new java.math.BigDecimal("150000"))));

    var service = useCase.execute(organizationId, batchId).services().getFirst();

    assertThat(service.serviceCode()).isEqualTo("S1");
    assertThat(service.serviceName()).isEqualTo("Khám tổng quát");

    when(catalog.findByIds(java.util.Set.of(known))).thenReturn(java.util.List.of());
    var unnamed = useCase.execute(organizationId, batchId).services().getFirst();
    assertThat(unnamed.serviceCode()).isNull();
    assertThat(unnamed.serviceName()).isNull();
  }

  @Test
  void anInactiveOrganizationStillShowsItsExistingBatches() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    when(batches.findDetails(organizationId, batchId, false))
        .thenReturn(Optional.of(details(draftBatch(organizationId, batchId, 0, UUID.randomUUID()))));

    assertThat(useCase.execute(organizationId, batchId).id()).isEqualTo(batchId);
  }

  @Test
  void unknownOrganizationIsNotFound() {
    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(batches);
  }

  @Test
  void aBatchOfAnotherOrganizationOrADeletedBatchIsNotFound() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, false)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void rejectsMissingIdentifiers() {
    assertThatThrownBy(() -> useCase.execute(null, batchId))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(organizations, batches);
  }
}
