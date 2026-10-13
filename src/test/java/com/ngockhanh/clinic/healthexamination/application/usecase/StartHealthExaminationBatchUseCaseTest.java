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
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.StartHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
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

class StartHealthExaminationBatchUseCaseTest {
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final StartHealthExaminationBatchUseCase useCase =
      new StartHealthExaminationBatchUseCase(organizations, batches, catalog, audit);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID service = UUID.randomUUID();

  @BeforeEach
  void anActiveOrganizationWithADraftAtVersionTwo() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(Optional.of(details(draftBatch(organizationId, batchId, 2, service))));
    when(batches.findDetails(organizationId, batchId, false))
        .thenReturn(
            Optional.of(
                details(batch(organizationId, batchId, BatchStatus.READY, 3, null, service))));
  }

  private static StartHealthExaminationBatchCommand version(Long rowVersion) {
    return StartHealthExaminationBatchCommand.builder().rowVersion(rowVersion).build();
  }

  @Test
  void movesADraftToReadyStoresOnlyTheStatusAndAuditsBothVersions() {
    var started = useCase.execute(organizationId, batchId, version(2L), actor);

    assertThat(started.status()).isEqualTo("READY");
    assertThat(started.rowVersion()).isEqualTo(3);
    var stored = ArgumentCaptor.forClass(HealthExaminationBatch.class);
    var order = inOrder(batches, audit);
    order.verify(batches).updateStatus(stored.capture(), eq(BatchStatus.DRAFT), eq(2L));
    assertThat(stored.getValue().status()).isEqualTo(BatchStatus.READY);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
    order
        .verify(audit)
        .record(
            eq(actor),
            eq("START_HEALTH_EXAMINATION_BATCH"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(batchId),
            before.capture(),
            after.capture());
    assertThat(before.getValue()).containsEntry("status", "DRAFT").containsEntry("rowVersion", 2L);
    assertThat(after.getValue()).containsEntry("status", "READY").containsEntry("rowVersion", 3L);
  }

  @Test
  void aStaleVersionIsAConflictAndNothingIsStored() {
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(1L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verify(batches, never()).updateStatus(any(), any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void onlyADraftCanBeStarted() {
    for (BatchStatus status :
        List.of(BatchStatus.READY, BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      when(batches.findDetails(organizationId, batchId, true))
          .thenReturn(Optional.of(details(batch(organizationId, batchId, status, 2, null, service))));

      assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
          .as(status.name())
          .isInstanceOf(DomainRuleViolation.class);
    }
    verify(batches, never()).updateStatus(any(), any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anInactiveOrganizationCannotStartABatchAndAnUnknownOneIsNotFound() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(batches, never()).updateStatus(any(), any(), anyLong());
  }

  @Test
  void aDeletedOrUnknownBatchIsNotFound() {
    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void aConflictWhileStoringIsNotAuditedAndPropagates() {
    doThrow(new ConcurrentUpdateException())
        .when(batches)
        .updateStatus(any(), any(), anyLong());

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, version(2L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verifyNoInteractions(audit);
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
    verify(batches, never()).updateStatus(any(), any(), anyLong());
  }
}
