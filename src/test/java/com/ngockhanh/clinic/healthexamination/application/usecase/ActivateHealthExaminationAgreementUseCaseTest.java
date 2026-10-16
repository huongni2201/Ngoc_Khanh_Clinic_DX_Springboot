package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.AgreementFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.ActivateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.service.AgreementSupport;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivateHealthExaminationAgreementUseCaseTest {
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final ActivateHealthExaminationAgreementUseCase useCase =
      new ActivateHealthExaminationAgreementUseCase(
          organizations, agreements, new AgreementSupport(catalog), audit);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID service = UUID.randomUUID();

  @BeforeEach
  void anActiveOrganizationWithADraftAtVersionTwo() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(Optional.of(details(draftAgreement(organizationId, agreementId, 2, service))));
    when(agreements.findDetails(organizationId, agreementId, false))
        .thenReturn(
            Optional.of(
                details(
                    agreement(
                        organizationId, agreementId, AgreementStatus.ACTIVE, 3, null, service))));
  }

  private static ActivateHealthExaminationAgreementCommand version(Long rowVersion) {
    return ActivateHealthExaminationAgreementCommand.builder().rowVersion(rowVersion).build();
  }

  @Test
  void movesADraftToActiveStoresOnlyTheStatusAndAuditsBothVersions() {
    var started = useCase.execute(organizationId, agreementId, version(2L), actor);

    assertThat(started.status()).isEqualTo("ACTIVE");
    assertThat(started.rowVersion()).isEqualTo(3);
    var stored = ArgumentCaptor.forClass(HealthExaminationAgreement.class);
    var order = inOrder(agreements, audit);
    order.verify(agreements).updateStatus(stored.capture(), eq(AgreementStatus.DRAFT), eq(2L));
    assertThat(stored.getValue().status()).isEqualTo(AgreementStatus.ACTIVE);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
    order
        .verify(audit)
        .record(
            eq(actor),
            eq("ACTIVATE_HEALTH_EXAMINATION_AGREEMENT"),
            eq("HEALTH_EXAMINATION_AGREEMENT"),
            eq(agreementId),
            before.capture(),
            after.capture());
    assertThat(before.getValue()).containsEntry("status", "DRAFT").containsEntry("rowVersion", 2L);
    assertThat(after.getValue()).containsEntry("status", "ACTIVE").containsEntry("rowVersion", 3L);
  }

  @Test
  void aStaleVersionIsAConflictAndNothingIsStored() {
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(1L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verify(agreements, never()).updateStatus(any(), any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void onlyADraftCanBeStarted() {
    for (AgreementStatus status : List.of(AgreementStatus.ACTIVE, AgreementStatus.ENDED)) {
      when(agreements.findDetails(organizationId, agreementId, true))
          .thenReturn(
              Optional.of(
                  details(agreement(organizationId, agreementId, status, 2, null, service))));

      assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
          .as(status.name())
          .isInstanceOf(DomainRuleViolation.class);
    }
    verify(agreements, never()).updateStatus(any(), any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anInactiveOrganizationCannotStartAAgreementAndAnUnknownOneIsNotFound() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(agreements, never()).updateStatus(any(), any(), anyLong());
  }

  @Test
  void aDeletedOrUnknownAgreementIsNotFound() {
    when(agreements.findDetails(organizationId, agreementId, true)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void aConflictWhileStoringIsNotAuditedAndPropagates() {
    doThrow(new ConcurrentUpdateException()).when(agreements).updateStatus(any(), any(), anyLong());

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsMissingArgumentsAndInvalidVersions() {
    assertThatThrownBy(() -> useCase.execute(null, agreementId, version(2L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, null, version(2L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, null, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(null), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(-1L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    verify(agreements, never()).updateStatus(any(), any(), anyLong());
  }
}
