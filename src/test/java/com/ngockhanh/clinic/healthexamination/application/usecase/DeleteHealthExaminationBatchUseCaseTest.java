package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
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
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.integration.application.query.BatchHistoryQuery;
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

class DeleteHealthExaminationBatchUseCaseTest {
  private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");

  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final BatchHistoryQuery history = mock(BatchHistoryQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final DeleteHealthExaminationBatchUseCase useCase =
      new DeleteHealthExaminationBatchUseCase(
          organizations, batches, history, audit, Clock.fixed(NOW, ZoneOffset.UTC));
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();

  @BeforeEach
  void anExistingOrganizationWithADraftAtVersionTwo() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(
            Optional.of(details(draftBatch(organizationId, batchId, 2, UUID.randomUUID()))));
  }

  private static DeleteHealthExaminationBatchCommand version(Long rowVersion) {
    return DeleteHealthExaminationBatchCommand.builder().rowVersion(rowVersion).build();
  }

  @Test
  void softDeletesAnUnusedDraftAtTheClockTimeAndAudits() {
    useCase.execute(organizationId, batchId, version(2L), actor);

    var deleted = ArgumentCaptor.forClass(HealthExaminationBatch.class);
    var order = inOrder(batches, history, audit);
    order.verify(batches).hasParticipants(batchId);
    order.verify(history).hasBatchReferences(batchId);
    order.verify(batches).softDelete(deleted.capture(), eq(2L));
    assertThat(deleted.getValue().deletedAt()).isEqualTo(NOW);
    assertThat(deleted.getValue().isDeleted()).isTrue();
    assertThat(deleted.getValue().status()).isEqualTo(BatchStatus.DRAFT);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
    order
        .verify(audit)
        .record(
            eq(actor),
            eq("DELETE_HEALTH_EXAMINATION_BATCH"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(batchId),
            before.capture(),
            after.capture());
    assertThat(before.getValue()).containsEntry("rowVersion", 2L).doesNotContainKey("deletedAt");
    assertThat(after.getValue())
        .containsEntry("rowVersion", 3L)
        .containsEntry("deletedAt", NOW.toString());
  }

  @Test
  void aStaleVersionIsAConflictAndNothingIsChecked() {
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(1L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verify(batches, never()).softDelete(any(), anyLong());
    verifyNoInteractions(history, audit);
  }

  @Test
  void onlyADraftCanBeDeleted() {
    for (BatchStatus status :
        List.of(BatchStatus.READY, BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      when(batches.findDetails(organizationId, batchId, true))
          .thenReturn(
              Optional.of(
                  details(batch(organizationId, batchId, status, 2, null, UUID.randomUUID()))));

      assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
          .as(status.name())
          .isInstanceOf(DomainRuleViolation.class);
    }
    verify(batches, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aBatchWithAnyParticipantCannotBeDeletedWhateverTheRosterStatus() {
    when(batches.hasParticipants(batchId)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(batches, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aBatchReferencedByImportHistoryCannotBeDeleted() {
    when(history.hasBatchReferences(batchId)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(batches, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anAlreadyDeletedOrUnknownBatchIsNotFoundEvenWithTheSameVersion() {
    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(batches, never()).softDelete(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void unknownOrganizationIsNotFoundButAnInactiveOneAllowsDeletion() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    useCase.execute(organizationId, batchId, version(2L), actor);
    verify(batches).softDelete(any(), eq(2L));

    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void aConflictWhileStoringIsNotAuditedAndPropagates() {
    doThrow(new ConcurrentUpdateException()).when(batches).softDelete(any(), anyLong());

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verifyNoInteractions(audit);
  }

  @Test
  void auditFailureIsNotSwallowedSoTheDeletionRollsBack() {
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("audit unavailable");
  }

  @Test
  void rejectsMissingArgumentsAndInvalidVersions() {
    assertThatThrownBy(() -> useCase.execute(null, batchId, version(2L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, null, version(2L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, null, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(null), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(-1L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    verify(batches, never()).softDelete(any(), anyLong());
  }
}
