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
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.integration.application.query.AgreementHistoryQuery;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeleteHealthExaminationAgreementUseCaseTest {
  private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");

  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  private final AgreementHistoryQuery history = mock(AgreementHistoryQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final DeleteHealthExaminationAgreementUseCase useCase =
      new DeleteHealthExaminationAgreementUseCase(
          organizations, agreements, history, audit, Clock.fixed(NOW, ZoneOffset.UTC));
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();

  @BeforeEach
  void anExistingOrganizationWithADraftAtVersionTwo() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(
            Optional.of(
                details(draftAgreement(organizationId, agreementId, 2, UUID.randomUUID()))));
  }

  private static DeleteHealthExaminationAgreementCommand version(Long rowVersion) {
    return DeleteHealthExaminationAgreementCommand.builder().rowVersion(rowVersion).build();
  }

  @Test
  void softDeletesAnUnusedDraftAtTheClockTimeAndAudits() {
    useCase.execute(organizationId, agreementId, version(2L), actor);

    var deleted = ArgumentCaptor.forClass(HealthExaminationAgreement.class);
    var order = inOrder(agreements, history, audit);
    order.verify(agreements).hasParticipants(agreementId);
    order.verify(agreements).hasSessions(agreementId);
    order.verify(history).hasAgreementReferences(agreementId);
    order.verify(agreements).softDelete(deleted.capture(), eq(2L));
    assertThat(deleted.getValue().deletedAt()).isEqualTo(NOW);
    assertThat(deleted.getValue().isDeleted()).isTrue();
    assertThat(deleted.getValue().status()).isEqualTo(AgreementStatus.DRAFT);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
    order
        .verify(audit)
        .record(
            eq(actor),
            eq("DELETE_HEALTH_EXAMINATION_AGREEMENT"),
            eq("HEALTH_EXAMINATION_AGREEMENT"),
            eq(agreementId),
            before.capture(),
            after.capture());
    assertThat(before.getValue()).containsEntry("rowVersion", 2L).doesNotContainKey("deletedAt");
    assertThat(after.getValue())
        .containsEntry("rowVersion", 3L)
        .containsEntry("deletedAt", NOW.toString());
  }

  @Test
  void aStaleVersionIsAConflictAndNothingIsChecked() {
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(1L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verify(agreements, never()).softDelete(any(), anyLong());
    verifyNoInteractions(history, audit);
  }

  @Test
  void onlyADraftCanBeDeleted() {
    for (AgreementStatus status : List.of(AgreementStatus.ACTIVE, AgreementStatus.ENDED)) {
      when(agreements.findDetails(organizationId, agreementId, true))
          .thenReturn(
              Optional.of(
                  details(
                      agreement(organizationId, agreementId, status, 2, null, UUID.randomUUID()))));

      assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
          .as(status.name())
          .isInstanceOf(DomainRuleViolation.class);
    }
    verify(agreements, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anAgreementWithAnyParticipantCannotBeDeletedWhateverTheRosterStatus() {
    when(agreements.hasParticipants(agreementId)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(agreements, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anAgreementWithASessionOfItsOwnCannotBeDeleted() {
    when(agreements.hasSessions(agreementId)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(agreements, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anAgreementReferencedByImportHistoryCannotBeDeleted() {
    when(history.hasAgreementReferences(agreementId)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(agreements, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anAlreadyDeletedOrUnknownAgreementIsNotFoundEvenWithTheSameVersion() {
    when(agreements.findDetails(organizationId, agreementId, true)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(agreements, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void unknownOrganizationIsNotFoundButAnInactiveOneAllowsDeletion() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    useCase.execute(organizationId, agreementId, version(2L), actor);
    verify(agreements).softDelete(any(), eq(2L));

    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void aConflictWhileStoringIsNotAuditedAndPropagates() {
    doThrow(new ConcurrentUpdateException()).when(agreements).softDelete(any(), anyLong());

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verifyNoInteractions(audit);
  }

  @Test
  void propagatesAuditFailureAfterSoftDelete() {
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, version(2L), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("audit unavailable");
    verify(agreements).softDelete(any(), eq(2L));
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
    verify(agreements, never()).softDelete(any(), anyLong());
  }
}
