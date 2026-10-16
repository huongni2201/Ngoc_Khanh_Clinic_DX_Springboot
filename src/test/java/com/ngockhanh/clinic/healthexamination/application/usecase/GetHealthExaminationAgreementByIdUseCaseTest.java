package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.AgreementFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetHealthExaminationAgreementByIdUseCaseTest {
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final GetHealthExaminationAgreementByIdUseCase useCase =
      new GetHealthExaminationAgreementByIdUseCase(organizations, agreements, catalog);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();

  @Test
  void returnsTheAgreementScopedByOrganizationAndAgreementWithoutLocking() {
    UUID service = UUID.randomUUID();
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, false))
        .thenReturn(Optional.of(details(draftAgreement(organizationId, agreementId, 5, service))));

    var response = useCase.execute(organizationId, agreementId);

    assertThat(response.id()).isEqualTo(agreementId);
    assertThat(response.organizationId()).isEqualTo(organizationId);
    assertThat(response.rowVersion()).isEqualTo(5);
    assertThat(response.validFrom()).isEqualTo(FIRST_DAY);
    assertThat(response.validTo()).isEqualTo(SECOND_DAY);
    assertThat(response.services()).hasSize(1);
    assertThat(response.services().getFirst().serviceId()).isEqualTo(service);
    verify(agreements).findDetails(organizationId, agreementId, false);
  }

  @Test
  void addsTheCatalogCodeAndNameToEachServiceAndLeavesUnknownServicesUnnamed() {
    UUID known = UUID.randomUUID();
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, false))
        .thenReturn(Optional.of(details(draftAgreement(organizationId, agreementId, 1, known))));
    when(catalog.findByIds(java.util.Set.of(known)))
        .thenReturn(
            java.util.List.of(
                new ServiceCatalogQuery.Service(
                    known, "S1", "Khám tổng quát", false, new java.math.BigDecimal("150000"))));

    var service = useCase.execute(organizationId, agreementId).services().getFirst();

    assertThat(service.serviceCode()).isEqualTo("S1");
    assertThat(service.serviceName()).isEqualTo("Khám tổng quát");

    when(catalog.findByIds(java.util.Set.of(known))).thenReturn(java.util.List.of());
    var unnamed = useCase.execute(organizationId, agreementId).services().getFirst();
    assertThat(unnamed.serviceCode()).isNull();
    assertThat(unnamed.serviceName()).isNull();
  }

  @Test
  void anInactiveOrganizationStillShowsItsExistingAgreements() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, false))
        .thenReturn(
            Optional.of(
                details(draftAgreement(organizationId, agreementId, 0, UUID.randomUUID()))));

    assertThat(useCase.execute(organizationId, agreementId).id()).isEqualTo(agreementId);
  }

  @Test
  void unknownOrganizationIsNotFound() {
    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(agreements);
  }

  @Test
  void anAgreementOfAnotherOrganizationOrADeletedAgreementIsNotFound() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, false)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void rejectsMissingIdentifiers() {
    assertThatThrownBy(() -> useCase.execute(null, agreementId))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(organizations, agreements);
  }
}
