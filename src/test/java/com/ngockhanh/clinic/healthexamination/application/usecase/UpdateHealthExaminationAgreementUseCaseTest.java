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
import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.service.AgreementSupport;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateHealthExaminationAgreementUseCaseTest {
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final UpdateHealthExaminationAgreementUseCase useCase =
      new UpdateHealthExaminationAgreementUseCase(
          organizations, agreements, new AgreementSupport(catalog), audit);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID kept = UUID.randomUUID();
  private final UUID dropped = UUID.randomUUID();
  private final UUID added = UUID.randomUUID();
  private HealthExaminationAgreement current;

  @BeforeEach
  void aDraftWithVersionThreeAndTwoServices() {
    current = draftAgreement(organizationId, agreementId, 3, kept, dropped);
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(Optional.of(details(current)));
  }

  private UpdateHealthExaminationAgreementCommand command(
      AgreementConfiguration config, Long version) {
    return UpdateHealthExaminationAgreementCommand.builder()
        .configuration(config)
        .rowVersion(version)
        .build();
  }

  private AgreementConfiguration replacement(UUID... services) {
    return configuration(services).toBuilder().validFrom(SECOND_DAY).validTo(null).build();
  }

  /** The row as the database would return it after the header version was incremented. */
  private void storedAfterUpdate() {
    when(agreements.findDetails(organizationId, agreementId, false))
        .thenAnswer(
            call -> {
              var updated = ArgumentCaptor.forClass(HealthExaminationAgreement.class);
              verify(agreements).update(updated.capture(), anyLong());
              var b = updated.getValue();
              return Optional.of(
                  details(
                      HealthExaminationAgreement.restoreConfiguration(
                          b.id(),
                          b.organizationId(),
                          b.code(),
                          b.name(),
                          b.validFrom(),
                          b.validTo(),
                          b.services(),
                          b.status(),
                          b.rowVersion() + 1,
                          null)));
            });
  }

  @Test
  void replacesTheConfigurationKeepingIdsAndAuditsBeforeAndAfter() {
    var keptService = current.services().getFirst();
    var removedService = current.services().getLast();
    when(catalog.findByIds(Set.of(added)))
        .thenReturn(
            List.of(
                new ServiceCatalogQuery.Service(added, "S2", "New", true, new BigDecimal("500"))));
    storedAfterUpdate();

    var response =
        useCase.execute(organizationId, agreementId, command(replacement(added, kept), 3L), actor);

    var updated = ArgumentCaptor.forClass(HealthExaminationAgreement.class);
    var order = inOrder(agreements, audit);
    order
        .verify(agreements)
        .findReferencedAgreementServiceIds(
            eq(agreementId), eq(Set.of(removedService.id().value())));
    order.verify(agreements).update(updated.capture(), eq(3L));
    assertThat(updated.getValue().code()).as("the code never changes").isEqualTo("B1");
    assertThat(updated.getValue().validFrom()).isEqualTo(SECOND_DAY);
    assertThat(updated.getValue().validTo()).isNull();
    assertThat(updated.getValue().services())
        .extracting(s -> s.serviceId().value())
        .containsExactly(added, kept);
    assertThat(updated.getValue().services().getLast().id()).isEqualTo(keptService.id());
    assertThat(response.rowVersion()).isEqualTo(4);
    assertThat(response.agreementCode()).isEqualTo("B1");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
    order
        .verify(audit)
        .record(
            eq(actor),
            eq("UPDATE_HEALTH_EXAMINATION_AGREEMENT"),
            eq("HEALTH_EXAMINATION_AGREEMENT"),
            eq(agreementId),
            before.capture(),
            after.capture());
    assertThat(before.getValue()).containsEntry("rowVersion", 3L).containsEntry("status", "DRAFT");
    assertThat(after.getValue()).containsEntry("rowVersion", 4L).containsEntry("status", "DRAFT");
  }

  @Test
  void aPutWithTheSameConfigurationStillStoresOnceSoTheVersionIsIncremented() {
    storedAfterUpdate();
    var originalServices = current.services();
    var same = configuration(kept, dropped);

    var response = useCase.execute(organizationId, agreementId, command(same, 3L), actor);

    verify(agreements).update(any(), eq(3L));
    assertThat(response.rowVersion()).isEqualTo(4);
    assertThat(current.services()).containsExactlyElementsOf(originalServices);
    verify(catalog, org.mockito.Mockito.times(2)).findByIds(Set.of(kept, dropped));
  }

  @Test
  void aStaleVersionIsAConflictAndNothingIsChanged() {
    assertThatThrownBy(
            () ->
                useCase.execute(organizationId, agreementId, command(replacement(kept), 2L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verify(agreements, never()).update(any(), anyLong());
    verifyNoInteractions(audit, catalog);
  }

  @Test
  void anAgreementThatIsNotADraftCannotBeUpdated() {
    for (AgreementStatus status : List.of(AgreementStatus.ACTIVE, AgreementStatus.ENDED)) {
      when(agreements.findDetails(organizationId, agreementId, true))
          .thenReturn(
              Optional.of(
                  details(agreement(organizationId, agreementId, status, 3, null, kept, dropped))));

      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId, agreementId, command(replacement(kept), 3L), actor))
          .as(status.name())
          .isInstanceOf(DomainRuleViolation.class);
    }
    verify(agreements, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void removingAServiceThatHasParticipantServicesIsRejectedEvenIfNeverPerformed() {
    when(agreements.findReferencedAgreementServiceIds(eq(agreementId), any()))
        .thenAnswer(call -> Set.copyOf((java.util.Collection<UUID>) call.getArgument(1)));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, agreementId, command(configuration(kept), 3L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(agreements, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aConflictDetectedWhileStoringIsNotAuditedAndPropagates() {
    doThrow(new ConcurrentUpdateException()).when(agreements).update(any(), anyLong());
    var same = configuration(kept, dropped);

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(same, 3L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verifyNoInteractions(audit);
  }

  @Test
  void propagatesAuditFailureAfterUpdate() {
    storedAfterUpdate();
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());
    var same = configuration(kept, dropped);

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(same, 3L), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("audit unavailable");
    verify(agreements).update(any(), eq(3L));
  }

  @Test
  void aNewServiceMustBeActiveInTheCatalogButAKeptOneIsNotRechecked() {
    when(catalog.findByIds(Set.of(added)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(added, "S2", "New", false, BigDecimal.TEN)));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, agreementId, command(replacement(kept, added), 3L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(agreements, never()).update(any(), anyLong());
  }

  @Test
  void unknownOrganizationOrAgreementIsNotFoundAndAnInactiveOrganizationIsAllowed() {
    storedAfterUpdate();
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    var same = configuration(kept, dropped);
    assertThat(useCase.execute(organizationId, agreementId, command(same, 3L), actor).rowVersion())
        .isEqualTo(4);

    when(agreements.findDetails(organizationId, agreementId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(same, 3L), actor))
        .isInstanceOf(ResourceNotFoundException.class);

    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(same, 3L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void rejectsMissingArgumentsAndInvalidVersions() {
    var config = replacement(kept);
    assertThatThrownBy(() -> useCase.execute(null, agreementId, command(config, 3L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, null, command(config, 3L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, null, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(null, 3L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(config, 3L), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(config, null), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(config, -1L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    verify(agreements, never()).update(any(), anyLong());
  }
}
